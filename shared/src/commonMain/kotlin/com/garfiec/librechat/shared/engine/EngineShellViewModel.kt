package com.garfiec.librechat.shared.engine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.MissionReadingPositions
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.datastore.ThemeDataStore
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.data.engine.ConversationRequests
import com.garfiec.librechat.core.data.engine.EngineAttentionWatcher
import com.garfiec.librechat.core.data.engine.EngineChatSummary
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.engine.OpenConversation
import com.garfiec.librechat.core.data.engine.SessionKindStore
import com.garfiec.librechat.core.data.portal.PortalSignOut
import com.garfiec.librechat.core.data.portal.isPortalSignedIn
import com.garfiec.librechat.core.data.scheduler.SchedulerRepository
import com.garfiec.librechat.core.model.scheduler.PortalIdentity
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
 * Every engine dependency is nullable, resolved with `getOrNull`: without the engine graph the
 * shell reads as signed out, and the sign-in screen says why.
 */
class EngineShellViewModel(
    private val settings: EngineSettingsStore?,
    private val tokens: EngineTokenStore?,
    private val portalSignOut: PortalSignOut?,
    private val repository: EngineMissionRepository?,
    private val kinds: SessionKindStore,
    private val positions: MissionReadingPositions,
    private val themeDataStore: ThemeDataStore,
    /** The Settings switch for sound and notifications (03/10/2026). */
    private val settingsDataStore: SettingsDataStore,
    /** Questions asked anywhere on the engine, rung while signed in. Null without the engine graph. */
    private val attentionWatcher: EngineAttentionWatcher? = null,
    /** The conversation a tapped notification asks to open. Null without the engine graph. */
    private val conversationRequests: ConversationRequests? = null,
    /** Who the portal says is signed in, for the drawer's foot (lot 3). Null without the engine graph. */
    private val scheduler: SchedulerRepository? = null,
) : ViewModel() {

    private val _signedIn = MutableStateFlow<Boolean?>(null)

    /** Null while it is being read; the shell draws nothing but the background meanwhile. */
    val signedIn: StateFlow<Boolean?> = _signedIn.asStateFlow()

    private val _chats = MutableStateFlow(EngineChatsState())
    val chats: StateFlow<EngineChatsState> = _chats.asStateFlow()

    private val _addresses = MutableStateFlow<EngineAccess?>(null)

    /** What the settings screen shows of the platform: read-only, changed by signing in again. */
    val addresses: StateFlow<EngineAccess?> = _addresses.asStateFlow()

    private val _identity = MutableStateFlow<PortalIdentity?>(null)

    /** The portal's name for the person, read once per sign-in; null until then, or when it has none. */
    val identity: StateFlow<PortalIdentity?> = _identity.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = themeDataStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), themeDataStore.initialThemeMode)

    /** Sound and notifications when a conversation needs the person, on by default. */
    val attentionSound: StateFlow<Boolean> = settingsDataStore.attentionSound
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    /** The conversation's text size, Settings › Apparence (lot 4). */
    val chatFontSize: StateFlow<ChatFontSize> = settingsDataStore.chatFontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatFontSize.MEDIUM)

    /** A conversation a notification asked to open, until the shell has opened it. */
    val openRequest: StateFlow<OpenConversation?> = conversationRequests?.pending
        ?: MutableStateFlow<OpenConversation?>(null).asStateFlow()

    private var chatsJob: Job? = null
    private var attentionJob: Job? = null

    init {
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
            if (now && before != true) {
                refreshChats()
                readIdentity()
            }
            if (now) watchAttention() else stopAttention()
        }
    }

    /** One small read per sign-in; the drawer's foot names the host alone until it lands, or for good. */
    private fun readIdentity() {
        val source = scheduler ?: return
        viewModelScope.launch { _identity.value = source.identity() }
    }

    /**
     * Listens for questions across the engine while signed in: a task left running asks, and the
     * person hears it wherever they are in the app, or outside it while the process lives. The
     * watch outlives the screen, not the activity: as long as this view model does.
     */
    private fun watchAttention() {
        val watcher = attentionWatcher ?: return
        if (attentionJob?.isActive == true) return
        attentionJob = viewModelScope.launch {
            try {
                watcher.watch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Attention: the question watch stopped" }
            }
        }
    }

    private fun stopAttention() {
        attentionJob?.cancel()
        attentionJob = null
    }

    fun setAttentionSound(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAttentionSound(enabled) }
    }

    fun setChatFontSize(size: ChatFontSize) {
        viewModelScope.launch { settingsDataStore.setChatFontSize(size) }
    }

    /** The shell opened the conversation a notification named: it is not opened twice. */
    fun consumeOpenRequest(conversation: OpenConversation) {
        conversationRequests?.consume(conversation)
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
            stopAttention()
            _chats.value = EngineChatsState()
            _identity.value = null
            _signedIn.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
