package com.garfiec.librechat.shared.engine.voice

import com.garfiec.librechat.core.model.scheduler.VoiceRoom
import kotlinx.coroutines.flow.StateFlow

/**
 * The voice mode's line to the voice agent (27/10/2026, step 3 of Raphaël's voice mode): a LiveKit
 * room the phone joins over data with the token the scheduler signed (`POST /vocal`,
 * [VoiceRooms]). The agent is dispatched to the room and hears the titulaire without a code; the
 * phone publishes its microphone and plays the agent's voice. The platform is Android only, so the
 * LiveKit side lives in `:app` ([VoiceLink] keeps the shell free of it).
 */
interface VoiceLink {
    val state: StateFlow<VoiceLinkState>

    /** Joins [url] with [token], microphone on; the state follows. */
    suspend fun connect(url: String, token: String)

    /** Leaves the room; a no-op when there is none. */
    fun disconnect()
}

sealed interface VoiceLinkState {
    data object Idle : VoiceLinkState
    data object Connecting : VoiceLinkState

    /**
     * In the room. [agentState] is the agent's own word (`lk.agent.state`: listening, thinking,
     * speaking), [agentText] its last sentence, [heardText] what it last understood of the person.
     */
    data class Live(val agentState: String = "", val agentText: String = "", val heardText: String = "") : VoiceLinkState

    data class Ended(val reason: String? = null) : VoiceLinkState
}

/** Opens a room at the scheduler (`POST /vocal`). */
fun interface VoiceRooms {
    suspend fun open(): VoiceRoom
}
