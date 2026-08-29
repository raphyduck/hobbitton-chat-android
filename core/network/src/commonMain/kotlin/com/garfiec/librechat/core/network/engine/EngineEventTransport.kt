package com.garfiec.librechat.core.network.engine

import kotlinx.coroutines.flow.Flow

/**
 * The byte transport for the engine's event feed, `GET /event`.
 *
 * **Global, not per-session, and that is the engine's shape rather than a shortcut.** The per-session
 * durable feed belongs to the v2 surface, which a mission never reaches: on a session launched
 * through `prompt_async`, `/api/session/{id}/event` does not answer at all (measured 29/08/2026 —
 * the request times out before headers). The classic feed carries every session's events and names
 * each one, so [EngineStreamClient] subscribes once and keeps what belongs to the session on screen.
 *
 * A deliberate sibling of [com.garfiec.librechat.core.network.sse.SseHttpTransport] rather than a
 * reuse of it: that one is welded to LibreChat — its bearer refresh, its account-switch barrier — and
 * the engine is a different host with its own Basic auth plus the Authelia bearer.
 *
 * Android-only for now, like the rest of the engine graph. Implementations emit byte chunks as they
 * arrive; framing and reconnection live in [EngineStreamClient].
 */
interface EngineEventTransport {
    fun stream(): Flow<ByteArray>
}
