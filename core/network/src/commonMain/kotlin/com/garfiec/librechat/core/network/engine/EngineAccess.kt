package com.garfiec.librechat.core.network.engine

/**
 * Where the engine lives and how to get in. Read from settings rather than compiled in: the same
 * build serves a phone pointed at `agent.hobbitton.at` and a laptop pointed at `127.0.0.1:4096`.
 */
data class EngineAccess(
    /** Base URL of the engine itself, e.g. `https://agent.hobbitton.at`. */
    val baseUrl: String,
    /** Issuer of the bearer — the Authelia portal, e.g. `https://auth.hobbitton.at`. */
    val issuerUrl: String,
    val clientId: String,
    /** The engine's own Basic credentials. Independent of the portal's. */
    val username: String,
    val password: String,
    /**
     * Base URL of the scheduler, e.g. `https://sched.hobbitton.at`. Blank when it has not been
     * set — and blank is a normal state, not a broken one: the engine works without it, and the
     * Tasks tab simply has no recurring missions to show.
     */
    val schedulerUrl: String = "",
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

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
 * Persistence of the engine's tokens, kept apart from LibreChat's.
 *
 * Two authorities, two lifetimes, two revocations: mixing them would mean a chat logout silently
 * dropping the engine's refresh token, and a portal session expiring while the chat still works.
 * The implementation lives with the rest of the encrypted storage; this interface is what the
 * network layer needs and no more.
 */
interface EngineTokenStore {
    suspend fun read(): EngineTokens?
    suspend fun write(tokens: EngineTokens)
    suspend fun clear()
}

/**
 * The engine's Basic password, kept out of ordinary preferences.
 *
 * Separate from [EngineTokenStore] because the two have different lifetimes: the password is what
 * the person configured and survives every logout, the tokens are a session and must not. One store
 * for both would make « forget my session » either too destructive or too timid.
 */
interface EnginePasswordStore {
    suspend fun read(): String?
    suspend fun write(password: String)
    suspend fun clear()
}
