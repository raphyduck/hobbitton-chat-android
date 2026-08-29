package com.garfiec.librechat.core.network.engine

import kotlinx.coroutines.flow.Flow

/**
 * The byte transport for the engine's durable event feed, `GET /api/session/{id}/event`.
 *
 * A deliberate sibling of [com.garfiec.librechat.core.network.sse.SseHttpTransport] rather than a
 * reuse of it: that one is welded to LibreChat — its streaming client, its bearer-refresh, its
 * account-switch barrier — and speaks LibreChat's two-phase chat protocol. The engine is a different
 * host with different credentials (its own Basic plus the Authelia bearer, both applied by
 * `EngineAuthPlugin` on the engine's own client) and a different, one-phase durable feed. Sharing the
 * class would mean teaching it two personalities; a second small interface is cheaper and clearer.
 *
 * Android-only for now, like the rest of the engine graph (see `EngineModule.android.kt`). The iOS
 * engine wiring does not exist yet, so neither does an iOS event transport — the day the engine lands
 * on iOS, this gets an `NWConnection` actual the way the LibreChat SSE transport already has one.
 *
 * Implementations emit byte chunks exactly as they arrive; all line framing and reconnection live in
 * [EngineStreamClient]. A non-2xx response is surfaced as
 * [com.garfiec.librechat.core.network.sse.SseHttpStatusException] so the client can branch on 401 /
 * 404 / other.
 */
interface EngineEventTransport {

    /**
     * @param after resume cursor: the engine replays durable events *after* this aggregate seq, then
     *   tails live. Null replays the session's whole history from the start — how a freshly opened
     *   chat reconstructs the conversation before continuing it.
     */
    fun stream(sessionId: String, after: String?): Flow<ByteArray>
}
