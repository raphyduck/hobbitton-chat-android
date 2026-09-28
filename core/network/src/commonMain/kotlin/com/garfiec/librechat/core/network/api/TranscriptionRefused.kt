package com.garfiec.librechat.core.network.api

/**
 * The scheduler's transcription route said no, or answered something that is not a transcription.
 *
 * Its own type rather than an `EngineHttpException`, because the two things a screen needs are
 * here as fields: the [status] tells a file the server will not take (400) from a caller it will
 * not serve (403) and from a transcription service that is down (502); the [reason] is the
 * server's own `erreur`, which names the actual problem — « fichier trop volumineux » — in words
 * no status code carries.
 */
class TranscriptionRefused(
    val status: Int,
    val reason: String?,
) : Exception("Transcription refused with HTTP $status${reason?.let { ": $it" }.orEmpty()}")
