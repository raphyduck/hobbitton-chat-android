package com.garfiec.librechat.core.network.engine

/**
 * Drops every connection the app's HTTP clients hold, so that the next request opens a fresh one.
 *
 * Why (26/10/2026, Raphaël: opening a conversation stayed on its placeholders for 30 s to a minute,
 * and worked on a later tap): the engine's client multiplexes everything, the two event feeds
 * included, over one HTTP/2 connection. After a sleep or a change of network that connection can be
 * dead without the phone knowing: writes still go into the socket, nothing ever comes back. OkHttp
 * only evicts a multiplexed connection on a reset or a shutdown, never on a request that timed out,
 * and the screen's own timeout cancels the request before OkHttp's would have sent its probe ping.
 * Every attempt then queued on the same dead connection. A hung request, or a network that changed,
 * is the moment to throw the connections away: in-flight calls fail at once (the feeds reconnect by
 * themselves), and the retry gets a live connection.
 */
fun interface ConnectionReset {
    /** Suspends until every connection is gone: the caller's retry then opens a fresh one. Off the main thread. */
    suspend fun dropAll()
}

/** Where nothing holds connections (tests, previews). */
object NoConnectionReset : ConnectionReset {
    override suspend fun dropAll() = Unit
}
