package com.garfiec.librechat.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.engineFailureKind
import com.garfiec.librechat.core.model.engine.EngineFailureKind
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
 * [chat] is folded from the engine's durable event stream: opening the screen replays the session's
 * whole history and then tails the live reply, so the same [MissionChatState] shows the past and what
 * is happening right now. [sending] covers the brief gap between the send POST and the first event of
 * the answer; after that the answer's arrival is what tells the user it is working, and
 * [MissionChatState.streaming] drives the stop button.
 */
data class MissionChatUiState(
    val chat: MissionChatState = MissionChatState(),
    val input: String = "",
    val sending: Boolean = false,
    /** Why the last send did not reach the engine, or null. The text is put back when this is set. */
    val sendError: EngineFailureKind? = null,
)

class MissionChatViewModel(
    private val sessionId: String,
    private val repository: EngineMissionRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MissionChatUiState())
    val uiState: StateFlow<MissionChatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    init {
        openStream()
    }

    /**
     * Subscribe to the session's event feed. The flow never throws — [EngineStreamClient] reports a
     * dead stream as an [com.garfiec.librechat.core.model.engine.EngineStreamEvent.Failed] event, so
     * it reduces into the visible state rather than tearing the collector down.
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
                withContext(ioDispatcher) { repository.sendMessage(sessionId, text) }
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

    /** Stop a reply in progress. The engine ends the run; the stream reports the finish. */
    fun stop() {
        viewModelScope.launch {
            runCatching { withContext(ioDispatcher) { repository.abort(sessionId) } }
        }
    }

    fun dismissSendError() {
        _uiState.update { it.copy(sendError = null) }
    }
}
