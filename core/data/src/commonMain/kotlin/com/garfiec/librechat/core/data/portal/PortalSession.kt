package com.garfiec.librechat.core.data.portal

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.network.engine.EngineAccess
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import com.garfiec.librechat.core.network.engine.EngineTokens
import com.garfiec.librechat.core.network.engine.PortalBearerSource
import com.garfiec.librechat.core.network.engine.auth.EngineGrantRefused
import com.garfiec.librechat.core.network.engine.auth.EngineOAuthEndpoints
import com.garfiec.librechat.core.network.engine.auth.EngineTokenClient
import com.garfiec.librechat.core.network.engine.auth.EngineTokenResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one holder of the portal's tokens: keeps the bearer usable, and knows when it can no longer
 * do so.
 *
 * **One for everything the portal opens (D-076).** The engine's client and the scheduler's client
 * both read their bearer here — the same token, whose audience names both — and the sign-in writes
 * it here, whether it runs from the login screen or from the Tasks tab. It was called
 * `EngineSessionManager` while the engine was the only thing it served; the scheduler joined, then
 * the single login, and the name had stopped saying what it held.
 *
 * Separate from LibreChat's [com.garfiec.librechat.core.network.client.TokenManager] on purpose:
 * two authorities, two lifetimes, two revocations. A chat session expiring must not drop the
 * portal's refresh token, nor a dead portal session leave the chat looking healthy. An explicit
 * sign-out ends both, deliberately: [forget] is called by `PortalSignOut`.
 */
class PortalSession(
    private val store: EngineTokenStore,
    private val client: EngineTokenClient,
    private val endpoints: suspend () -> EngineOAuthEndpoints?,
    private val now: () -> Long,
) : PortalBearerSource {

    /**
     * Serialises renewals. Without it, a screen that fires five requests at once on a cold start
     * sends five refreshes with the same token; the server rotates on the first and answers
     * `invalid_grant` to the other four, and the app logs the user out on a token that had just
     * been renewed successfully.
     */
    private val gate = Mutex()

    /** The token to present, refreshing it first if it is spent. Null means: go through the portal. */
    override suspend fun bearer(): String? {
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
    override suspend fun renew(): String? = gate.withLock {
        val stored = store.read() ?: return@withLock null
        if (stored.isFresh(now())) return@withLock stored.accessToken

        val refreshToken = stored.refreshToken ?: run {
            // No `offline_access`, or a token issued before it was asked for. Nothing to renew
            // with — and pretending otherwise would loop the caller through a doomed retry.
            Logger.i("Portal") { "No refresh token for the engine — the portal has to be visited again" }
            store.clear()
            return@withLock null
        }

        val renewed = try {
            client.refresh(requireEndpoints(), refreshToken)
        } catch (refused: EngineGrantRefused) {
            // Destructive, and only here. The portal will not honour this pair again — keeping it
            // reproduces the same refusal on every later call.
            Logger.i("Portal") { "The portal refused the renewal (${refused.error}) — the portal has to be visited again" }
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
            Logger.w("Portal", unreachable) { "Engine token renewal could not reach the portal — session kept" }
            return@withLock null
        }

        // `previous` matters: a server that does not rotate answers without a refresh token, and
        // dropping the one we hold would end the session at the *following* renewal — far from the
        // change that caused it.
        store.write(renewed.toTokens(now(), previous = stored))
        renewed.accessToken
    }

    /**
     * Whether a portal session is held at all — tokens stored, fresh or renewable. Not a promise
     * that the next renewal succeeds: a refused one clears the store, and the next check says so.
     */
    suspend fun hasTokens(): Boolean = store.read() != null

    /** Stores what the code exchange produced, at the end of a portal round trip. */
    suspend fun onAuthorized(response: EngineTokenResponse) {
        store.write(response.toTokens(now()))
    }

    /**
     * Drops the tokens — the local half of signing out. Serialised with [renew] so a renewal in
     * flight cannot write a fresh pair back after the store was cleared.
     */
    suspend fun forget() = gate.withLock {
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
/**
 * The app's one signed-in state since D-077: the three addresses are set **and** the portal holds
 * tokens. There is no other login — no LibreChat account, no server URL — so this is what decides
 * between the sign-in screen and the chat.
 */
fun isPortalSignedIn(access: EngineAccess?, tokens: EngineTokens?): Boolean =
    access != null && access.isConfigured && access.hasScheduler && tokens != null

internal fun EngineTokenResponse.toTokens(
    nowEpochSeconds: Long,
    previous: EngineTokens? = null,
): EngineTokens = EngineTokens(
    accessToken = accessToken,
    refreshToken = refreshToken ?: previous?.refreshToken,
    expiresAtEpochSeconds = expiresIn?.let { nowEpochSeconds + it },
)
