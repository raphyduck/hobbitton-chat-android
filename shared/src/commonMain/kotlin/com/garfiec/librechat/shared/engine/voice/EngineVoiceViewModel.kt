package com.garfiec.librechat.shared.engine.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.network.engine.EngineHttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The voice screen's state: a phase, the agent's words, and what went wrong if anything. */
data class EngineVoiceUiState(
    val phase: Phase = Phase.IDLE,
    val agentState: String = "",
    val agentText: String = "",
    val heardText: String = "",
    /** Null when nothing failed; [serverUnconfigured] says the scheduler has no LiveKit settings. */
    val error: String? = null,
    val serverUnconfigured: Boolean = false,
) {
    enum class Phase { IDLE, OPENING, LIVE, ENDED }
}

/**
 * Opens a room at the scheduler, joins it through the [VoiceLink], and hands the screen what to show.
 * Null sources (a device without the engine graph) leave the screen on its error.
 */
class EngineVoiceViewModel(
    private val rooms: VoiceRooms?,
    private val link: VoiceLink?,
) : ViewModel() {

    private val _state = MutableStateFlow(EngineVoiceUiState())
    val state: StateFlow<EngineVoiceUiState> = _state.asStateFlow()

    init {
        link?.let { line ->
            viewModelScope.launch {
                line.state.collect { linkState ->
                    _state.update { it.follow(linkState) }
                }
            }
        }
    }

    /** Opens the room and joins it; a second call while live does nothing. */
    fun start() {
        if (_state.value.phase == EngineVoiceUiState.Phase.OPENING || _state.value.phase == EngineVoiceUiState.Phase.LIVE) return
        val source = rooms
        val line = link
        if (source == null || line == null) {
            _state.update { it.copy(phase = EngineVoiceUiState.Phase.IDLE, error = "no engine", serverUnconfigured = true) }
            return
        }
        _state.update { EngineVoiceUiState(phase = EngineVoiceUiState.Phase.OPENING) }
        viewModelScope.launch {
            try {
                val room = source.open()
                line.connect(room.url, room.jeton)
            } catch (e: EngineHttpException) {
                Logger.w(e) { "Voice room refused (${e.status})" }
                _state.update {
                    it.copy(
                        phase = EngineVoiceUiState.Phase.IDLE,
                        error = e.message ?: e.status.toString(),
                        serverUnconfigured = e.status == UNCONFIGURED_STATUS || e.status == NO_ROUTE_STATUS,
                    )
                }
            } catch (e: Exception) {
                Logger.w(e) { "Voice mode could not start" }
                _state.update { it.copy(phase = EngineVoiceUiState.Phase.IDLE, error = e.message ?: e::class.simpleName) }
            }
        }
    }

    fun stop() {
        link?.disconnect()
    }

    override fun onCleared() {
        stop()
    }

    private fun EngineVoiceUiState.follow(linkState: VoiceLinkState): EngineVoiceUiState = when (linkState) {
        VoiceLinkState.Idle -> if (phase == EngineVoiceUiState.Phase.LIVE) copy(phase = EngineVoiceUiState.Phase.ENDED) else this
        VoiceLinkState.Connecting -> copy(phase = EngineVoiceUiState.Phase.OPENING, error = null)
        is VoiceLinkState.Live -> copy(
            phase = EngineVoiceUiState.Phase.LIVE,
            agentState = linkState.agentState,
            agentText = linkState.agentText,
            heardText = linkState.heardText,
            error = null,
        )
        is VoiceLinkState.Ended -> copy(phase = EngineVoiceUiState.Phase.ENDED, error = linkState.reason)
    }

    private companion object {
        const val UNCONFIGURED_STATUS = 503
        const val NO_ROUTE_STATUS = 404
    }
}
