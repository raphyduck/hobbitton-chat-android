package com.garfiec.librechat.shared.engine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.MissionReadingPositions
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.datastore.ThemeDataStore
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.data.engine.ChatScope
import com.garfiec.librechat.core.data.engine.ConversationReads
import com.garfiec.librechat.core.data.engine.ConversationRequests
import com.garfiec.librechat.core.data.engine.EngineAttentionWatcher
import com.garfiec.librechat.core.data.engine.EngineChatSummary
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineModelChoice
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.engine.OpenConversation
import com.garfiec.librechat.core.data.engine.SessionKindStore
import com.garfiec.librechat.core.data.portal.PortalSignOut
import com.garfiec.librechat.core.data.portal.isPortalSignedIn
import com.garfiec.librechat.core.data.scheduler.SchedulerRepository
import com.garfiec.librechat.core.logging.DiagnosticLogShare
import com.garfiec.librechat.core.model.scheduler.PortalIdentity
import com.garfiec.librechat.core.network.engine.EngineAccess
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** A list of conversations (the drawer's, or the Conversations page's), and whether it could be read. */
data class EngineChatsState(
    val chats: List<EngineChatSummary> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    /** Older sessions remain past this read: the lists offer « Plus anciennes » (21/10/2026). */
    val more: Boolean = false,
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
    /** What this device has shown of each conversation: forgotten at sign-out (16/10/2026). */
    private val reads: ConversationReads,
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
    /** The phone's share sheet for the diagnostic log (Settings → Plateforme, 20/10/2026). Null in tests. */
    private val diagnostics: DiagnosticLogShare? = null,
) : ViewModel() {

    private val _signedIn = MutableStateFlow<Boolean?>(null)

    /** Null while it is being read; the shell draws nothing but the background meanwhile. */
    val signedIn: StateFlow<Boolean?> = _signedIn.asStateFlow()

    private val _allChats = MutableStateFlow(EngineChatsState())

    /** The Conversations page: every session, the scheduler's runs included, unread first. */
    val allChats: StateFlow<EngineChatsState> = _allChats.asStateFlow()

    /**
     * The drawer: the same sessions as [allChats], from the same read (19/10/2026, Raphaël's rule:
     * the two lists show the same sessions), most recently active first under its day headers.
     */
    val chats: StateFlow<EngineChatsState> = _allChats
        .map { state -> state.copy(chats = state.chats.sortedByDescending { it.lastActivityMillis ?: Long.MIN_VALUE }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EngineChatsState())

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

    /** The mic heard by the phone itself rather than the scheduler, on by default (19/10/2026). */
    val localDictation: StateFlow<Boolean> = settingsDataStore.localDictation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    /** The conversation's text size, Settings › Apparence (lot 4). */
    val chatFontSize: StateFlow<ChatFontSize> = settingsDataStore.chatFontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatFontSize.MEDIUM)

    /** The app's language: a BCP-47 tag, or `system` to follow the phone (19/10/2026). */
    val selectedLanguage: StateFlow<String> = settingsDataStore.selectedLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), settingsDataStore.initialSelectedLanguage)

    /** The model a new conversation starts on, by id; null follows the engine's (19/10/2026). */
    val defaultChatModel: StateFlow<String?> = settingsDataStore.defaultChatModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _models = MutableStateFlow(EngineModelChoice())

    /** What Settings offers as the default model: read when Settings opens, empty until then. */
    val models: StateFlow<EngineModelChoice> = _models.asStateFlow()

    /** A conversation a notification asked to open, until the shell has opened it. */
    val openRequest: StateFlow<OpenConversation?> = conversationRequests?.pending
        ?: MutableStateFlow<OpenConversation?>(null).asStateFlow()

    private var allChatsJob: Job? = null

    /** How many sessions the lists read: a page more each « Plus anciennes » (21/10/2026). */
    private var shown = PAGE

    private val _search = MutableStateFlow<EngineChatsState?>(null)

    /**
     * The drawer's search over every session the engine holds, accents folded (21/10/2026); null
     * while nothing is typed. Before, it filtered only the rows already drawn.
     */
    val search: StateFlow<EngineChatsState?> = _search.asStateFlow()
    private var searchJob: Job? = null
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

    /** Settings → Plateforme: the diagnostic log to whatever the person picks to send it with. */
    fun shareDiagnostics() {
        val share = diagnostics ?: return
        viewModelScope.launch {
            try {
                share.share()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Diagnostics: the log could not be shared" }
            }
        }
    }

    fun setAttentionSound(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAttentionSound(enabled) }
    }

    fun setLocalDictation(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setLocalDictation(enabled) }
    }

    fun setChatFontSize(size: ChatFontSize) {
        viewModelScope.launch { settingsDataStore.setChatFontSize(size) }
    }

    fun setSelectedLanguage(tag: String) {
        viewModelScope.launch { settingsDataStore.setSelectedLanguage(tag) }
    }

    fun setDefaultChatModel(modelId: String?) {
        viewModelScope.launch { settingsDataStore.setDefaultChatModel(modelId) }
    }

    /**
     * Reads the models the default can be set to, on each opening of Settings: the deployment's
     * list may have moved. A failure leaves the last list, or none, and the row names what is set.
     */
    fun loadModels() {
        val source = repository ?: return
        viewModelScope.launch {
            try {
                _models.value = source.models()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Settings: the engine's models could not be read" }
            }
        }
    }

    /** The shell opened the conversation a notification named: it is not opened twice. */
    fun consumeOpenRequest(conversation: OpenConversation) {
        conversationRequests?.consume(conversation)
    }

    fun onSignedIn() {
        recheck()
    }

    /** Re-reads the drawer's chats: the Conversations page's read, which both lists show. */
    fun refreshChats() = refreshAllChats()

    /**
     * Re-reads the sessions, for the Conversations page and the drawer alike. One read at a time:
     * the drawer asks on every opening, and a second read racing the first would only land the
     * same list twice.
     */
    fun refreshAllChats() {
        val source = repository ?: return
        if (allChatsJob?.isActive == true) return
        allChatsJob = viewModelScope.launch {
            _allChats.update { it.copy(loading = true) }
            try {
                val limit = shown
                val chats = source.recentChats(scope = ChatScope.ALL, limit = limit)
                _allChats.value = EngineChatsState(chats = chats, more = chats.size >= limit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Conversations: the engine's sessions could not be read" }
                _allChats.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    /** « Plus anciennes », at the foot of both lists: a page more, read at once. */
    fun loadOlder() {
        shown += PAGE
        allChatsJob?.cancel()
        refreshAllChats()
    }

    /**
     * Searches every session's title for [query], after a short pause so each keystroke does not
     * read the engine; a blank query clears it. The latest query wins.
     */
    fun search(query: String) {
        searchJob?.cancel()
        val source = repository
        if (query.isBlank() || source == null) {
            _search.value = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_PAUSE_MS)
            _search.update { (it ?: EngineChatsState()).copy(loading = true) }
            try {
                val found = source.recentChats(scope = ChatScope.ALL, limit = PAGE, query = query)
                _search.value = EngineChatsState(
                    chats = found.sortedByDescending { it.lastActivityMillis ?: Long.MIN_VALUE },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Search: the engine's sessions could not be read" }
                _search.value = EngineChatsState(failed = true)
            }
        }
    }

    /**
     * [sessionId] was on screen until now: what it said is read. Called when a conversation opens
     * and when it is left, so a reply that arrived while it was shown does not light it up.
     * The lists drop its unread mark at once, without waiting for their next read.
     */
    fun markRead(sessionId: String) {
        val source = repository ?: return
        _allChats.update { state -> state.copy(chats = state.chats.map { if (it.sessionId == sessionId) it.copy(unread = false) else it }) }
        viewModelScope.launch { source.markRead(sessionId, Clock.System.now().toEpochMilliseconds()) }
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
                runCatching { reads.clear() }.onFailure { Logger.w(it) { "Could not clear the read marks" } }
                runCatching { positions.clear() }.onFailure { Logger.w(it) { "Could not clear the reading positions" } }
            }
            allChatsJob?.cancel()
            stopAttention()
            _allChats.value = EngineChatsState()
            shown = PAGE
            searchJob?.cancel()
            _search.value = null
            _identity.value = null
            _signedIn.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Sessions per read of the lists, and per « Plus anciennes ». */
        const val PAGE = 200

        /** The typing pause before a search reads the engine. */
        const val SEARCH_PAUSE_MS = 300L
    }
}
