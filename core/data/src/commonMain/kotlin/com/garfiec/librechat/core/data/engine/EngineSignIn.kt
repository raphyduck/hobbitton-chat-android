package com.garfiec.librechat.core.data.engine

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.network.engine.EngineAccess
import com.garfiec.librechat.core.network.engine.auth.EngineAuthorizationAttempt
import com.garfiec.librechat.core.network.engine.auth.EngineOAuthEndpoints
import com.garfiec.librechat.core.network.engine.auth.EngineTokenClient
import com.garfiec.librechat.core.network.engine.auth.FormPostCallback
import com.garfiec.librechat.core.network.engine.auth.authorizationUrl
import com.garfiec.librechat.core.network.engine.auth.generatePkcePair
import com.garfiec.librechat.core.network.engine.auth.generateStateToken
import com.garfiec.librechat.core.network.engine.auth.loopbackRedirectUri

/**
 * The first sign-in — the half of the portal round trip that was never wired.
 *
 * Everything downstream of it existed and was tested: PKCE, the pushed authorization request, the
 * loopback callback parser, the code exchange, the token store, the renewal. Nothing **called**
 * them. [EngineSessionManager.onAuthorized] had no caller in the whole application, and neither did
 * `pushAuthorizationRequest`, `authorizationUrl` or `parseFormPostCallback` outside their own unit
 * tests. The app could therefore renew a token it had no way of ever obtaining.
 *
 * What that looked like on 24 August, and why it resisted three attempts to fix it by re-entering
 * credentials: the Tasks tab said « sign-in to the engine failed », which is true and reads like a
 * password problem. Authelia's debug log settled it — a probe carrying a deliberately bogus bearer
 * produced « the bearer token does not appear to be a relevant access token », while the app's own
 * requests produced **no such line at all**. It was sending no token, because it had none, because
 * nothing had ever asked the portal for one.
 *
 * ## Why a socket and not an app scheme
 *
 * Authelia refuses `authelia.bearer.authz` with anything but `response_mode=form_post`, and a form
 * POST cannot be delivered to `at.hobbitton.chat://…` — an App Link intent drops the body. So the
 * app listens on `127.0.0.1` for exactly one request. That is RFC 8252 §7.3, and it is why the
 * client is registered with a **port-less** redirect: the OS picks the port per exchange.
 */
class EngineSignIn(
    /**
     * Where the engine and the portal are. A lambda rather than the settings store itself: this
     * class needs one answer from it, and taking the whole store would drag DataStore and the
     * encrypted password store into every test of the round trip.
     */
    private val access: suspend () -> EngineAccess?,
    private val tokens: EngineTokenClient,
    private val sessions: EngineSessionManager,
    private val listener: () -> EngineCallbackListener,
    private val endpoints: suspend (issuerUrl: String) -> EngineOAuthEndpoints?,
) {

    /**
     * Runs the whole round trip and stores the tokens.
     *
     * [openBrowser] is passed in rather than resolved here so this class stays free of any UI
     * dependency and testable without a device; on Android the screen hands it a `UriHandler`.
     */
    suspend fun signIn(openBrowser: (url: String) -> Unit): EngineSignInResult {
        val engine = access() ?: return EngineSignInResult.NotConfigured
        if (engine.issuerUrl.isBlank()) return EngineSignInResult.NotConfigured

        val discovered = endpoints(engine.issuerUrl)
            ?: return EngineSignInResult.PortalUnreachable("discovery failed for ${engine.issuerUrl}")
        if (discovered.parEndpoint == null) {
            // Falling back to a plain authorize URL would put scopes, challenge and state back into
            // browser history. Better to say why than to quietly weaken the flow.
            return EngineSignInResult.PortalUnreachable("the portal advertises no PAR endpoint")
        }

        val socket = listener()
        return try {
            val attempt = EngineAuthorizationAttempt(
                pkce = generatePkcePair(),
                state = generateStateToken(),
                redirectUri = loopbackRedirectUri(socket.open()),
            )

            val pushed = runCatching {
                tokens.pushAuthorizationRequest(discovered, attempt)
            }.getOrElse { failure ->
                Logger.w("Engine", failure) { "The pushed authorization request was refused" }
                return EngineSignInResult.PortalUnreachable(failure.message ?: "PAR refused")
            }

            openBrowser(
                authorizationUrl(
                    endpoints = discovered,
                    clientId = engine.clientId,
                    requestUri = pushed.requestUri,
                ),
            )

            when (val callback = socket.await(CALLBACK_TIMEOUT_MS)) {
                is FormPostCallback.Success -> exchange(discovered, attempt, callback)
                is FormPostCallback.Failure -> EngineSignInResult.Refused(
                    error = callback.error,
                    description = callback.description,
                )
                is FormPostCallback.Malformed -> EngineSignInResult.Interrupted(callback.reason)
            }
        } finally {
            socket.close()
        }
    }

    private suspend fun exchange(
        discovered: EngineOAuthEndpoints,
        attempt: EngineAuthorizationAttempt,
        callback: FormPostCallback.Success,
    ): EngineSignInResult {
        // Checked before the code is spent, not after. A returned state that is not ours means the
        // response belongs to someone else's request — the one attack this parameter exists to stop
        // — and redeeming the code first would defeat the check entirely.
        if (callback.state != attempt.state) {
            Logger.w("Engine") { "The portal returned a state that is not this attempt's" }
            return EngineSignInResult.Interrupted("state mismatch")
        }

        return runCatching { tokens.exchangeCode(discovered, callback.code, attempt) }
            .fold(
                onSuccess = { response ->
                    sessions.onAuthorized(response)
                    // Saying so plainly: without `authelia.bearer.authz` the token is an ordinary
                    // OIDC access token, the proxy ignores it, and the tab fails exactly as it did
                    // before — with nothing on screen to distinguish the two.
                    val granted = response.scope?.split(' ').orEmpty()
                    if (granted.isNotEmpty() && BEARER_AUTHZ !in granted) {
                        Logger.w("Engine") { "The portal granted $granted, without $BEARER_AUTHZ" }
                        return EngineSignInResult.MissingAuthorizationScope(granted)
                    }
                    EngineSignInResult.Authorized
                },
                onFailure = { failure ->
                    Logger.w("Engine", failure) { "The code exchange failed" }
                    EngineSignInResult.Interrupted(failure.message ?: "code exchange failed")
                },
            )
    }

    private companion object {
        const val BEARER_AUTHZ = "authelia.bearer.authz"

        /**
         * Five minutes. The second factor is in here — a code read off another device, sometimes a
         * hardware key — and a thirty-second timeout would close the socket while the person is
         * still typing, leaving an error that blames the portal for their pace.
         */
        const val CALLBACK_TIMEOUT_MS = 5 * 60 * 1000L
    }
}

/**
 * The one-request loopback socket, behind an interface because `ServerSocket` is a JVM class and
 * this orchestration is not.
 */
interface EngineCallbackListener {
    /** Binds `127.0.0.1` on a port the OS chooses, and returns it. */
    suspend fun open(): Int

    /** Waits for the callback, answers the browser with a closing page, and reports what it read. */
    suspend fun await(timeoutMillis: Long): FormPostCallback

    /** Releases the socket. Called even when the flow failed — especially then. */
    fun close()
}

/**
 * What came of it, in the terms that change what the person should do next — the same rule as
 * [com.garfiec.librechat.core.model.engine.EngineFailureKind], for the same reason.
 */
sealed interface EngineSignInResult {
    data object Authorized : EngineSignInResult

    /** No engine address or no portal address stored: there is nothing to sign in to. */
    data object NotConfigured : EngineSignInResult

    /** The portal never answered, or answered something unusable. Retrying is reasonable. */
    data class PortalUnreachable(val reason: String) : EngineSignInResult

    /** The portal said no: consent refused, second factor abandoned, request expired. */
    data class Refused(val error: String, val description: String?) : EngineSignInResult

    /** The round trip broke somewhere it should not have. Retrying is reasonable. */
    data class Interrupted(val reason: String) : EngineSignInResult

    /**
     * Signed in, and the token still will not open the engine.
     *
     * Its own outcome because it is the one failure that looks like a success: tokens are stored,
     * the flow completed, and every request keeps landing on the portal. It means the client is not
     * allowed to ask for `authelia.bearer.authz` — a server-side configuration matter, not
     * something the person can fix by trying again.
     */
    data class MissingAuthorizationScope(val granted: List<String>) : EngineSignInResult
}

/**
 * Ce que l'écran appelle pour déclencher un tour de portail — une interface parce que
 * l'implémentation est forcément propre à la plateforme : elle a besoin d'un socket local et d'un
 * service au premier plan, dont aucun n'existe en code partagé.
 *
 * L'écran n'attend PAS un résultat rendu : il observe [etat]. C'est le correctif du 24/08 — celui
 * qui a lancé la connexion n'est pas forcément là quand elle se termine, puisque le navigateur passe
 * devant et que l'écran peut être détruit entre-temps.
 */
interface EngineSignInLauncher {
    val etat: kotlinx.coroutines.flow.StateFlow<EngineSignInProgress>

    /** Lance le tour, ou ne fait rien s'il en reste un en vol. */
    fun lancer(ouvrirNavigateur: (url: String) -> Unit)

    /** Remet l'état à zéro une fois le résultat lu, pour qu'un second essai reparte propre. */
    fun acquitter()
}

/** Où en est le tour, pour un écran qui peut être arrivé au milieu. */
sealed interface EngineSignInProgress {
    data object Idle : EngineSignInProgress
    data object EnCours : EngineSignInProgress
    data class Termine(val issue: EngineSignInResult) : EngineSignInProgress
}

