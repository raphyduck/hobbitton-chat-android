package com.garfiec.librechat.voice

import android.content.Context
import co.touchlab.kermit.Logger
import com.garfiec.librechat.shared.engine.voice.VoiceLink
import com.garfiec.librechat.shared.engine.voice.VoiceLinkState
import io.livekit.android.AudioOptions
import io.livekit.android.AudioType
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The voice mode's room on LiveKit (27/10/2026): the microphone published, the agent's voice played
 * on the speaker (a media stream, not a call: no earpiece), the agent's state and transcriptions
 * read off the room. One room at a time; [disconnect] releases it. While joined, [VoiceCallService]
 * keeps the microphone open when the app leaves the foreground (28/10/2026).
 */
class LiveKitVoiceLink(private val context: Context) : VoiceLink {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<VoiceLinkState>(VoiceLinkState.Idle)
    override val state: StateFlow<VoiceLinkState> = _state
    private var room: Room? = null
    private var watching: Job? = null

    override suspend fun connect(url: String, token: String) {
        disconnect()
        _state.value = VoiceLinkState.Connecting
        // Before the room, while the screen is in front: Android 14 starts a microphone service
        // from the foreground only.
        VoiceCallService.start(context)
        val opened = LiveKit.create(
            appContext = context,
            overrides = LiveKitOverrides(audioOptions = AudioOptions(audioOutputType = AudioType.MediaAudioType())),
        )
        room = opened
        opened.registerTextStreamHandler(TRANSCRIPTION_TOPIC) { reader, _ ->
            val ofMine = reader.info.attributes[TRANSCRIBED_TRACK] in opened.localParticipant.trackPublications.keys.map { it.toString() }
            scope.launch {
                val text = StringBuilder()
                try {
                    reader.flow.collect { piece ->
                        text.append(piece)
                        live { if (ofMine) copy(heardText = text.toString()) else copy(agentText = text.toString()) }
                    }
                } catch (e: Exception) {
                    Logger.d { "Transcription stream closed: ${e.message}" }
                }
            }
        }
        watching = scope.launch {
            opened.events.collect { event ->
                when (event) {
                    is RoomEvent.Disconnected -> {
                        _state.value = VoiceLinkState.Ended(event.reason.name.lowercase())
                        VoiceCallService.stop(context)
                    }
                    is RoomEvent.ParticipantAttributesChanged ->
                        event.changedAttributes[AGENT_STATE]?.let { word -> live { copy(agentState = word) } }
                    is RoomEvent.ParticipantConnected ->
                        event.participant.attributes[AGENT_STATE]?.let { word -> live { copy(agentState = word) } }
                    else -> Unit
                }
            }
        }
        try {
            opened.connect(url, token)
            opened.localParticipant.setMicrophoneEnabled(true)
        } catch (e: Exception) {
            // The room refused: nothing joined, so neither the room nor the service stay behind.
            disconnect()
            throw e
        }
        val agentState = opened.remoteParticipants.values.firstNotNullOfOrNull { it.attributes[AGENT_STATE] }.orEmpty()
        _state.value = VoiceLinkState.Live(agentState = agentState)
    }

    override fun disconnect() {
        watching?.cancel()
        watching = null
        room?.let { opened ->
            opened.unregisterTextStreamHandler(TRANSCRIPTION_TOPIC)
            opened.disconnect()
            opened.release()
        }
        room = null
        VoiceCallService.stop(context)
        if (_state.value !is VoiceLinkState.Ended) _state.value = VoiceLinkState.Idle
    }

    private inline fun live(change: VoiceLinkState.Live.() -> VoiceLinkState.Live) {
        _state.update { current -> if (current is VoiceLinkState.Live) current.change() else current }
    }

    private companion object {
        const val TRANSCRIPTION_TOPIC = "lk.transcription"
        const val TRANSCRIBED_TRACK = "lk.transcribed_track_id"
        const val AGENT_STATE = "lk.agent.state"
    }
}
