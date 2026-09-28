package com.garfiec.librechat.shared.engine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.datastore.MissionReadingPositions
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.datastore.ThemeDataStore
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.data.engine.EngineChatSummary
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.engine.SessionKindStore
import com.garfiec.librechat.core.data.portal.PortalSignOut
import com.garfiec.librechat.core.data.portal.isPortalSignedIn
import com.garfiec.librechat.core.data.prefetch.PrefetchScheduler
import com.garfiec.librechat.core.network.engine.EngineAccess
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The drawer's conversations, and whether they could be read. */
data class EngineChatsState(
    val chats: List<EngineChatSummary> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/**
 * The engine shell's own state (D-077): signed in or not, the drawer's chats, and what signing out
 * owes the device.
 *
 * **Signed in = the three addresses are set and the portal holds tokens** ([isPortalSignedIn]).
 * There is no other identity in the app any more — no LibreChat account, no server URL, no token
 * refresh against a chat server — so nothing here touches LibreChat's graph, and nothing started
 * from here calls a LibreChat endpoint.
 *
 * Every engine dependency is nullable: the engine graph is Android-only (D-034), and this binding
 * lives in the shared module both platforms start. On a platform without it the shell reads as
 * signed out, and the sign-in screen says why.
 */
class EngineShellViewModel(
    private val settings: EngineSettingsStore?,
    private val tokens: EngineTokenStore?,
    private val portalSignOut: PortalSignOut?,
    private val repository: EngineMissionRepository?,
    private val kinds: SessionKindStore,
    private val positions: MissionReadingPositions,
    private val themeDataStore: ThemeDataStore,
    settingsDataStore: SettingsDataStore,
    prefetchScheduler: PrefetchScheduler,
) : ViewModel() {

    private val _signedIn = MutableStateFlow<Boolean?>(null)

    /** Null while it is being read; the shell draws nothing but the background meanwhile. */
    val signedIn: StateFlow<Boolean?> = _signedIn.asStateFlow()

    private val _chats = MutableStateFlow(EngineChatsState())
    val chats: StateFlow<EngineChatsState> = _chats.asStateFlow()

    private val _addresses = MutableStateFlow<EngineAccess?>(null)

    /** What the settings screen shows of the platform: read-only, changed by signing in again. */
    val addresses: StateFlow<EngineAccess?> = _addresses.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = themeDataStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), themeDataStore.initialThemeMode)

    private var chatsJob: Job? = null

    init {
        // LibreChat's background prefetch is the one piece of its machinery that runs with no
        // screen at all (a periodic WorkManager job, opt-in). There is no LibreChat server any
        // more: switch it off and cancel what an earlier build scheduled, so nothing wakes up to
        // call it. Local writes only.
        viewModelScope.launch {
            runCatching {
                settingsDataStore.setPrefetchEnabled(false)
                prefetchScheduler.cancel()
            }.onFailure { Logger.w(it) { "Could not retire LibreChat's background prefetch" } }
        }
        recheck()
    }

    /**
     * Reads the signed-in state again. Called at start, on every return to the foreground, and
     * after a sign-in: a renewal the portal refused clears the tokens behind the screen's back, and
     * this is where the shell notices and goes back to the sign-in.
     */
    fun recheck() {
        viewModelScope.launch {
            val access = runCatching { settings?.access() }.getOrNull()
            val held = runCatching { tokens?.read() }.getOrNull()
            _addresses.value = access
            val now = isPortalSignedIn(access, held)
            val before = _signedIn.value
            _signedIn.value = now
            if (now && before != true) refreshChats()
        }
    }

    fun onSignedIn() {
        recheck()
    }

    /**
     * Re-reads the drawer's chats. One read at a time: the drawer asks on every opening, and a
     * second read racing the first would only land the same list twice.
     */
    fun refreshChats() {
        val source = repository ?: return
        if (chatsJob?.isActive == true) return
        chatsJob = viewModelScope.launch {
            _chats.update { it.copy(loading = true) }
            try {
                val chats = source.recentChats()
                _chats.value = EngineChatsState(chats = chats)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Drawer: the engine's chats could not be read" }
                _chats.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themeDataStore.setThemeMode(mode) }
    }

    /**
     * Signs out (D-077): the portal's tokens and the web view's cookies ([PortalSignOut], as the
     * D-076 logout did), then this device's chat caches — which session was a chat, where each
     * transcript was left. The addresses stay: they are not a secret, and the next sign-in starts
     * from them.
     *
     * Non-cancellable: leaving the screen that asked must not leave half a sign-out behind.
     */
    fun signOut() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                runCatching { portalSignOut?.onSignedOut(serverUrl = null) }
                    .onFailure { Logger.w(it) { "Portal sign-out failed" } }
                runCatching { kinds.clear() }.onFailure { Logger.w(it) { "Could not clear the session kinds" } }
                runCatching { positions.clear() }.onFailure { Logger.w(it) { "Could not clear the reading positions" } }
            }
            chatsJob?.cancel()
            _chats.value = EngineChatsState()
            _signedIn.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
