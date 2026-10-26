package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EngineMessage
import kotlinx.serialization.Serializable

/**
 * The tail of a conversation as the phone last saw it (26/10/2026, Raphaël: « quand tu reviens
 * dans une conversation qu'on a déjà ouverte, il ne redemande pas nécessairement le reste »).
 *
 * The engine stays the source of truth: this is a copy of its last answer, shown at once when the
 * conversation reopens, and refreshed unless the session has not moved since ([updated], the
 * engine's `time.updated`; null when it could not be read, and then the next opening refreshes).
 * Only the tail is kept ([limit] messages, the opening's read), never the whole transcript.
 */
@Serializable
data class CachedTranscript(
    val updated: Long?,
    val limit: Int?,
    val messages: List<EngineMessage>,
)

/**
 * Where the tails live on the device: a few dozen conversations at most, compressed, the oldest
 * forgotten first. Nothing is held in memory between two openings. Cleared at sign-out.
 */
interface TranscriptCache {
    suspend fun read(sessionId: String): CachedTranscript?
    suspend fun write(sessionId: String, transcript: CachedTranscript)
    suspend fun clear()
}
