package com.garfiec.librechat.core.data.engine

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.network.engine.EngineAccess
import com.garfiec.librechat.core.network.engine.auth.EngineAuthorizationAttempt
import com.garfiec.librechat.core.network.engine.auth.EngineOAuthEndpoints
import com.garfiec.librechat.core.network.engine.auth.EngineTokenClient
import com.garfiec.librechat.core.network.engine.auth.FormPostCallback
import com.garfiec.librechat.core.network.engine.auth.authorizationUrl
import com.garfiec.librechat.core.network.engine.auth.callbackRedirectUri
import com.garfiec.librechat.core.network.engine.auth.generatePkcePair
import com.garfiec.librechat.core.network.engine.auth.generateStateToken

/**
 * The first sign-in — the half of the portal round trip that was never wired.
 *
 * Everything downstream of it existed and was tested: PKCE, the pushed authorization request, the
 * callback parser, the code exchange, the token store, the renewal. Nothing **called**
 * them. [EngineSessionManager.onAuthorized] had no caller in the whole application, and neither did
 * `pushAuthorizationRequest`, `authorizationUrl` or `parseCallbackUri` outside their own unit
 * tests. The app could therefore renew a token it had no way of ever obtaining.
 *
 * What that looked like on 24 August, and why it resisted three attempts to fix it by re-entering
 * credentials: the Tasks tab said « sign-in to the engine failed », which is true and reads like a
 * password problem. Authelia's debug log settled it — a probe carrying a deliberately bogus bearer
 * produced « the bearer token does not appear to be a relevant access token », while the app's own
 * requests produced **no such line at all**. It was sending no token, because it had none, because
 * nothing had ever asked the portal for one.
 *
 * ## Par où le code revient
 *
 * Authelia refuse `authelia.bearer.authz` avec autre chose que `response_mode=form_post`, et un
 * POST de formulaire ne peut pas être délivré à `at.hobbitton.chat://…` — une App Link perd le
 * corps. La première réponse a été une socket sur `127.0.0.1` (RFC 8252 §7.3) ; elle a échoué sur
 * l'appareil, et pour une raison qui ne se corrige pas : Android gèle une application passée en
 * arrière-plan, et son `accept()` ne s'exécute plus pendant que la personne est dans son navigateur.
 *
 * Le POST atterrit donc sur une route HTTPS du planificateur, qui rebondit vers le schéma
 * applicatif en GET. Un lien profond **réveille** l'application au lieu de supposer qu'elle tourne.
 * Conséquence ici : plus de socket à ouvrir, plus de port à annoncer, et [EngineCallbackInbox] à la
 * place — voir D-049 côté serveur.
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
    /**
     * La boîte aux lettres du lien profond. Une instance, pas une fabrique : le point d'entrée de
     * la plateforme y dépose, ce tour y relève, et deux instances feraient deux boîtes dont l'une
     * ne serait jamais relevée.
     */
    private val inbox: EngineCallbackInbox,
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

        // La porte de retour. Sans elle le portail n'a nulle part où renvoyer le code, et c'est son
        // propre échec — distinct de « rien n'est configuré », qui a une autre réparation.
        val redirect = callbackRedirectUri(engine.schedulerUrl)
            ?: return EngineSignInResult.NoCallbackHost

        val discovered = endpoints(engine.issuerUrl)
            ?: return EngineSignInResult.PortalUnreachable("discovery failed for ${engine.issuerUrl}")
        if (discovered.parEndpoint == null) {
            // Falling back to a plain authorize URL would put scopes, challenge and state back into
            // browser history. Better to say why than to quietly weaken the flow.
            return EngineSignInResult.PortalUnreachable("the portal advertises no PAR endpoint")
        }

        // Armée AVANT que le navigateur ne s'ouvre. Sur un appareil rapide — session du portail
        // déjà valide, second facteur mémorisé — le retour peut arriver avant que cette coroutine
        // n'atteigne `attendre`, et une boîte armée après serait une boîte qui rate son propre
        // courrier.
        inbox.armer()
        return try {
            val attempt = EngineAuthorizationAttempt(
                pkce = generatePkcePair(),
                state = generateStateToken(),
                redirectUri = redirect,
            )

            val pushed = runCatching {
                // Les audiences sont RÉCLAMÉES, pas héritées : la liste du client côté portail dit
                // ce qui est permis, pas ce qui est accordé. Sans elles, tout le tour réussit et le
                // jeton obtenu est refusé à chaque requête en `invalid_target` — l'échec du 25/08,
                // qui se lisait à l'écran comme « le moteur n'a pas répondu ».
                tokens.pushAuthorizationRequest(
                    endpoints = discovered,
                    attempt = attempt,
                    audiences = listOf(engine.baseUrl, engine.schedulerUrl),
                )
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

            when (val callback = inbox.attendre(CALLBACK_TIMEOUT_MS)) {
                is FormPostCallback.Success -> exchange(discovered, attempt, callback)
                is FormPostCallback.Failure -> EngineSignInResult.Refused(
                    error = callback.error,
                    description = callback.description,
                )
                is FormPostCallback.Malformed -> EngineSignInResult.Interrupted(callback.reason)
            }
        } finally {
            inbox.liberer()
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
 * What came of it, in the terms that change what the person should do next — the same rule as
 * [com.garfiec.librechat.core.model.engine.EngineFailureKind], for the same reason.
 */
sealed interface EngineSignInResult {
    data object Authorized : EngineSignInResult

    /** No engine address or no portal address stored: there is nothing to sign in to. */
    data object NotConfigured : EngineSignInResult

    /**
     * Le planificateur n'a pas d'adresse, donc le portail n'a nulle part où renvoyer le code.
     *
     * Son propre résultat parce que sa réparation est propre : ce n'est pas « configurez tout »,
     * c'est « il manque CE réglage-là ». La route de retour vit sur le planificateur (D-049), ce
     * qui fait de son adresse — facultative pour tout le reste — la condition de la connexion.
     */
    data object NoCallbackHost : EngineSignInResult

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
 * l'implémentation a besoin d'une portée qui survive à l'écran, et que c'est la plateforme qui la
 * fournit.
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
