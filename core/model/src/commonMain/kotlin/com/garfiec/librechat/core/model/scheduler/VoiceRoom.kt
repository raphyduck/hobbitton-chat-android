package com.garfiec.librechat.core.model.scheduler

import kotlinx.serialization.Serializable

/**
 * The voice mode's room (27/10/2026): what the scheduler's `POST /vocal` answers, a LiveKit room the
 * phone joins with a token the scheduler signed behind the portal. The voice agent is dispatched to
 * it and finds the titulaire there, no code to read.
 */
@Serializable
data class VoiceRoom(
    val url: String,
    val salle: String,
    val jeton: String,
)
