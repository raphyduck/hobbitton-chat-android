package com.garfiec.librechat.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.engine.ConnectorOption
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.engineFailureKind
import com.garfiec.librechat.core.data.engine.offered
import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.feature.tasks.util.MissionChatState
import com.garfiec.librechat.feature.tasks.util.reduce
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One mission session, as a conversation you can talk to.
 *
 * Opening it does two things in order: fetch the transcript — the only place a mission's past lives —
 * and subscribe to the engine's feed for what happens next. Both arrive as the same event type, so
 * [chat] is one fold with no seam between them.
 */
data class MissionChatUiState(
    val chat: MissionChatState = MissionChatState(),
    val input: String = "",
    /** The transcript is still loading: the screen shows a spinner rather than a false empty state. */
    val loadingHistory: Boolean = true,
    /** A send is in flight — the gap between the POST and the answer's first token. */
    val sending: Boolean = false,
    /** Why the transcript would not load, or null. */
    val historyError: EngineFailureKind? = null,
    /** Why the last send did not reach the engine, or null. The text is put back when this is set. */
    val sendError: EngineFailureKind? = null,
    /** The connectors this deployment offers, fetched from the scheduler — never a local copy. */
    val connectors: List<ConnectorOption> = emptyList(),
    /** Which of them this session currently carries. */
    val enabledConnectors: Set<String> = emptySet(),
    /** The models a message may be sent on, and which one the next one goes to. */
    val models: List<EngineSelectableModel> = emptyList(),
    val model: EngineSelectableModel? = null,
    /**
     * The connector catalogue would not load. The chip says so instead of offering an empty list —
     * an empty connector sheet reads as « this mission can have nothing », which is a different
     * claim, and the very outcome this screen exists to have fixed.
     */
    val connectorsError: EngineFailureKind? = null,
    /** The model list would not load. Independent of [connectorsError]: different host. */
    val modelsError: EngineFailureKind? = null,
    /**
     * The chat's own font-size setting, applied here too. One knob for both conversations —
     * a reader on LARGE was getting normal-size text in this tab only.
     */
    val fontScale: Float = 1f,
)

class MissionChatViewModel(
    private val sessionId: String,
    private val repository: EngineMissionRepository,
    private val settings: SettingsDataStore,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MissionChatUiState())
    val uiState: StateFlow<MissionChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    init {
        loadHistory()
        openStream()
        loadCatalogue()
        viewModelScope.launch {
            settings.chatFontSize.collect { size ->
                _uiState.update {
                    it.copy(
                        fontScale = when (size) {
                            ChatFontSize.SMALL -> 0.85f
                            ChatFontSize.MEDIUM -> 1f
                            ChatFontSize.LARGE -> 1.2f
                        },
                    )
                }
            }
        }
    }

    /**
     * What the composer needs to offer: the connectors this deployment has, and the models a
     * message can be sent on.
     *
     * **Two fetches, two failures, on purpose.** They come from two different hosts — the connector
     * catalogue from the scheduler, the model list from the engine — so one being unreachable must
     * not take the other's picker down with it. Folding them into a single `try` did exactly that
     * on 30/08/2026: the scheduler had not yet been redeployed with its `connecteurs` tool, and the
     * model chip vanished along with the connector chip, on an engine that was answering fine.
     *
     * Either failure costs its own picker and nothing else: the transcript and the send box work
     * without both.
     */
    private fun loadCatalogue() {
        loadConnectors()
        loadModels()
    }

    private fun loadConnectors() {
        viewModelScope.launch {
            try {
                val catalogue = withContext(ioDispatcher) { repository.connectors() }
                _uiState.update {
                    it.copy(
                        // Someone is watching this conversation, so nothing is barred as it would be
                        // for an unattended mission (brief §4.2).
                        connectors = catalogue.offered(autonomous = false),
                        connectorsError = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(connectorsError = e.engineFailureKind()) }
            }
        }
    }

    private fun loadModels() {
        viewModelScope.launch {
            try {
                val choice = withContext(ioDispatcher) { repository.models() }
                _uiState.update {
                    it.copy(
                        models = choice.models,
                        model = it.model ?: choice.preselected,
                        modelsError = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(modelsError = e.engineFailureKind()) }
            }
        }
    }

    /** Retries only what failed — a working picker is not re-fetched to heal a broken one. */
    fun retryCatalogue() {
        if (_uiState.value.connectorsError != null) {
            _uiState.update { it.copy(connectorsError = null) }
            loadConnectors()
        }
        if (_uiState.value.modelsError != null) {
            _uiState.update { it.copy(modelsError = null) }
            loadModels()
        }
    }

    /**
     * Grants or revokes a connector on the live session.
     *
     * The screen moves first and the engine follows: a tick that waited for a round trip reads as a
     * broken checkbox. If the call fails the tick is rolled back, because leaving it on would
     * promise a capability the session does not have — and the next message would then fail for a
     * reason nothing on screen explains.
     */
    fun toggleConnector(name: String) {
        val before = _uiState.value.enabledConnectors
        val after = if (name in before) before - name else before + name
        _uiState.update { it.copy(enabledConnectors = after) }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { repository.setConnectors(sessionId, after.toList()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(enabledConnectors = before, sendError = e.engineFailureKind()) }
            }
        }
    }

    /** Which model the *next* message runs on. The engine takes it per message, not per session. */
    fun selectModel(model: EngineSelectableModel?) {
        _uiState.update { it.copy(model = model) }
    }

    /**
     * Seed the conversation from the transcript.
     *
     * The stream is opened alongside rather than after: a mission that is running right now would
     * otherwise have its first tokens dropped while the transcript is being fetched. Both fold into
     * the same state and the reducer is idempotent, so whichever lands first, the result is the same.
     */
    private fun loadHistory() {
        viewModelScope.launch {
            try {
                val events = withContext(ioDispatcher) { repository.history(sessionId) }
                _uiState.update { current ->
                    // Fold the past *under* whatever the live feed already delivered, so nothing that
                    // arrived while we were fetching is lost.
                    val seeded = events.fold(current.chat) { state, event -> state.reduce(event) }
                    current.copy(chat = seeded, loadingHistory = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(loadingHistory = false, historyError = e.engineFailureKind()) }
            }
        }
    }

    /**
     * Subscribe to the engine's feed. The flow never throws — a dead feed simply ends — so a failure
     * there leaves the transcript on screen instead of tearing the collector down.
     */
    private fun openStream() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            repository.events(sessionId)
                .flowOn(ioDispatcher)
                .collect { event ->
                    _uiState.update { it.copy(chat = it.chat.reduce(event)) }
                }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty() || _uiState.value.sending) return
        _uiState.update { it.copy(input = "", sending = true, sendError = null) }
        viewModelScope.launch {
            try {
                // The answer streams in over the feed while this call is in flight; what it returns is
                // the finished turn, folded in to reconcile anything the feed missed.
                val model = _uiState.value.model?.ref
                val settled = withContext(ioDispatcher) { repository.sendMessage(sessionId, text, model) }
                _uiState.update { current ->
                    current.copy(chat = settled.fold(current.chat) { state, event -> state.reduce(event) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never swallow the words: put them back in the box and name what went wrong.
                _uiState.update { it.copy(input = text, sendError = e.engineFailureKind()) }
            } finally {
                _uiState.update { it.copy(sending = false) }
            }
        }
    }

    /** Stop a reply in progress. The engine ends the run; the feed reports the session going idle. */
    fun stop() {
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { repository.abort(sessionId) } }
        }
    }

    fun retryHistory() {
        _uiState.update { it.copy(loadingHistory = true, historyError = null) }
        loadHistory()
    }

    fun dismissSendError() {
        _uiState.update { it.copy(sendError = null) }
    }
}
