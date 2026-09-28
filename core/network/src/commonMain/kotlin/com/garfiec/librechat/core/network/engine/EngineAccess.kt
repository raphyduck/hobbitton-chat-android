package com.garfiec.librechat.core.network.engine

/**
 * Where the engine lives and which portal vouches for the person. Read from settings rather than
 * compiled in: the same build serves a phone pointed at `agent.hobbitton.at` and a laptop pointed
 * at `127.0.0.1:4096`.
 *
 * **No engine password here any more (D-076).** The app used to keep the engine's Basic in its
 * encrypted store and send it on every request — a *service* secret, shared, with no per-device
 * revocation, on every phone. The edge now validates the portal's bearer and presents the Basic
 * itself, so the only credential this app holds for the engine and the scheduler is the portal's
 * token ([EngineTokens]). The client id is not a setting either: it is `PORTAL_CLIENT_ID`.
 */
data class EngineAccess(
    /** Base URL of the engine itself, e.g. `https://agent.hobbitton.at`. */
    val baseUrl: String,
    /** Issuer of the bearer — the Authelia portal, e.g. `https://auth.hobbitton.at`. */
    val issuerUrl: String,
    /**
     * Base URL of the scheduler, e.g. `https://sched.hobbitton.at`. Blank when it has not been
     * set — and blank is a normal state, not a broken one: the engine works without it, and the
     * Tasks tab simply has no recurring missions to show.
     */
    val schedulerUrl: String = "",
) {
    /**
     * An engine address *and* a portal: without the portal there is no bearer to obtain, and since
     * the Basic left the app (D-076) the bearer is the only way in.
     */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && issuerUrl.isNotBlank()

    /** Whether the scheduler is reachable — separate from [isConfigured], and optional. */
    val hasScheduler: Boolean
        get() = schedulerUrl.isNotBlank()
}

/**
 * The bearer Authelia issued, and what is needed to renew it.
 *
 * [expiresAtEpochSeconds] is stored rather than a duration: a duration is only meaningful next to
 * the instant it was measured from, and the app is suspended and resumed at the OS's convenience.
 */
data class EngineTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long?,
) {
    /**
     * Treats a token as spent slightly before it truly is. A request that leaves with three seconds
     * of validity arrives with none, and the failure it produces looks like an authorization
     * problem rather than the clock problem it is.
     */
    fun isFresh(nowEpochSeconds: Long, marginSeconds: Long = 30): Boolean =
        expiresAtEpochSeconds == null || nowEpochSeconds + marginSeconds < expiresAtEpochSeconds
}

/**
 * Persistence of the portal's tokens, kept apart from LibreChat's.
 *
 * Two authorities, two lifetimes, two revocations: mixing them would mean a chat session expiring
 * silently dropping the portal's refresh token, and a portal session expiring while the chat still
 * works. An explicit sign-out purges both (D-076) — that is a decision, not a side effect of
 * sharing a store. The implementation lives with the rest of the encrypted storage; this interface
 * is what the network layer needs and no more.
 */
interface EngineTokenStore {
    suspend fun read(): EngineTokens?
    suspend fun write(tokens: EngineTokens)
    suspend fun clear()
}
