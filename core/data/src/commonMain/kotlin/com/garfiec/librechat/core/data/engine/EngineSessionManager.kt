package com.garfiec.librechat.core.data.engine

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import com.garfiec.librechat.core.network.engine.EngineTokens
import com.garfiec.librechat.core.network.engine.auth.EngineGrantRefused
import com.garfiec.librechat.core.network.engine.auth.EngineOAuthEndpoints
import com.garfiec.librechat.core.network.engine.auth.EngineTokenClient
import com.garfiec.librechat.core.network.engine.auth.EngineTokenResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the engine's bearer usable, and knows when it can no longer do so.
 *
 * Separate from LibreChat's [com.garfiec.librechat.core.network.client.TokenManager] on purpose:
 * two authorities, two lifetimes, two revocations. Sharing one would mean a chat logout silently
 * dropping the portal's refresh token, and a dead portal session leaving the chat looking healthy.
 */
class EngineSessionManager(
    private val store: EngineTokenStore,
    private val client: EngineTokenClient,
    private val endpoints: suspend () -> EngineOAuthEndpoints?,
    private val now: () -> Long,
) {

    /**
     * Serialises renewals. Without it, a screen that fires five requests at once on a cold start
     * sends five refreshes with the same token; the server rotates on the first and answers
     * `invalid_grant` to the other four, and the app logs the user out on a token that had just
     * been renewed successfully.
     */
    private val gate = Mutex()

    /** The token to present, refreshing it first if it is spent. Null means: go through the portal. */
    suspend fun bearer(): String? {
        val current = store.read() ?: return null
        if (current.isFresh(now())) return current.accessToken
        return renew()
    }

    /**
     * Forces a renewal — what the HTTP plugin calls when the proxy turns a request away.
     *
     * Re-reads the store **inside** the lock: whoever waited here may have been queued behind a
     * renewal that already succeeded, and re-sending the token that was just rotated away is how a
     * working session gets thrown out.
     */
    suspend fun renew(): String? = gate.withLock {
        val stored = store.read() ?: return@withLock null
        if (stored.isFresh(now())) return@withLock stored.accessToken

        val refreshToken = stored.refreshToken ?: run {
            // No `offline_access`, or a token issued before it was asked for. Nothing to renew
            // with — and pretending otherwise would loop the caller through a doomed retry.
            Logger.i("Engine") { "No refresh token for the engine — the portal has to be visited again" }
            store.clear()
            return@withLock null
        }

        val renewed = try {
            client.refresh(requireEndpoints(), refreshToken)
        } catch (refused: EngineGrantRefused) {
            // Destructive, and only here. The portal will not honour this pair again — keeping it
            // reproduces the same refusal on every later call.
            Logger.i("Engine") { "The portal refused the renewal (${refused.error}) — the portal has to be visited again" }
            store.clear()
            return@withLock null
        } catch (cancellation: CancellationException) {
            // The screen went away mid-renewal. Nothing is wrong with the session, and swallowing
            // this would also break the caller's own cancellation.
            throw cancellation
        } catch (unreachable: Exception) {
            // No network, a proxy hiccup, a portal being restarted. The tokens are still valid:
            // forgetting them here is how a lost Wi-Fi second becomes a full second-factor login.
            // The caller gets null and fails this one request; the next one renews normally.
            Logger.w("Engine", unreachable) { "Engine token renewal could not reach the portal — session kept" }
            return@withLock null
        }

        // `previous` matters: a server that does not rotate answers without a refresh token, and
        // dropping the one we hold would end the session at the *following* renewal — far from the
        // change that caused it.
        store.write(renewed.toTokens(now(), previous = stored))
        renewed.accessToken
    }

    /** Stores what the code exchange produced, at the end of a portal round trip. */
    suspend fun onAuthorized(response: EngineTokenResponse) {
        store.write(response.toTokens(now()))
    }

    suspend fun forget() {
        store.clear()
    }

    private suspend fun requireEndpoints(): EngineOAuthEndpoints =
        endpoints() ?: error("The engine's OAuth endpoints are unknown — discovery has not run")
}

/**
 * `expires_in` is a duration, and a duration is only meaningful next to the instant it was measured
 * from. The app is suspended and resumed at the OS's convenience, so what gets stored is the
 * instant.
 *
 * A response that carries **no** new refresh token keeps the previous one — servers are allowed to
 * rotate or not, and dropping the old one on a non-rotating server would end the session at the
 * following renewal.
 */
internal fun EngineTokenResponse.toTokens(
    nowEpochSeconds: Long,
    previous: EngineTokens? = null,
): EngineTokens = EngineTokens(
    accessToken = accessToken,
    refreshToken = refreshToken ?: previous?.refreshToken,
    expiresAtEpochSeconds = expiresIn?.let { nowEpochSeconds + it },
)
