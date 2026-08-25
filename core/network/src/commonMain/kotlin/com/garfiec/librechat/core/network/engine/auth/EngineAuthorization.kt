package com.garfiec.librechat.core.network.engine.auth

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where Authelia advertises its OAuth endpoints. Read from
 * `/.well-known/openid-configuration` rather than hardcoded — the paths are Authelia's to change,
 * and a wrong one fails at the worst moment with an unhelpful 404.
 */
@Serializable
data class EngineOAuthEndpoints(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("pushed_authorization_request_endpoint") val parEndpoint: String? = null,
)

/**
 * The scopes this client asks for.
 *
 * `authelia.bearer.authz` is the one that matters: it is what makes the issued token usable as a
 * *bearer for authorization*, which the reverse proxy's `forward-auth` accepts in place of the
 * portal cookie. Without it the token is an ordinary OIDC access token and the proxy still sends
 * the app to the login page.
 *
 * `offline_access` buys the refresh token; without it the user re-authenticates, second factor
 * included, every time the access token expires.
 */
object EngineScopes {
    const val BEARER_AUTHZ = "authelia.bearer.authz"
    const val OFFLINE = "offline_access"
    val DEFAULT = listOf(BEARER_AUTHZ, OFFLINE)
}

/**
 * One authorization attempt: everything that has to survive from « open the browser » to « swap the
 * code for a token », and nothing that may be persisted. The [pkce] verifier in particular lives in
 * memory only — writing it down would defeat the point of manufacturing it per exchange.
 */
data class EngineAuthorizationAttempt(
    val pkce: PkcePair,
    val state: String,
    val redirectUri: String,
)

/**
 * Form fields for the pushed authorization request (RFC 9126).
 *
 * PAR rather than a plain authorize URL because the browser then carries only an opaque
 * `request_uri`: no scopes, no challenge, no state in a URL that lands in history, in the system
 * log, and in whatever the default browser syncs to the cloud.
 *
 * `response_mode=form_post` is **not** a preference. Authelia's own `validate-config` refuses a
 * client that asks for `authelia.bearer.authz` with anything else, and a form POST cannot be
 * delivered to an app scheme — which is why the redirect points at [callbackRedirectUri], an HTTPS
 * route on the server that bounces to the app scheme in a GET.
 */
fun pushedAuthorizationForm(
    clientId: String,
    attempt: EngineAuthorizationAttempt,
    scopes: List<String> = EngineScopes.DEFAULT,
): List<Pair<String, String>> = listOf(
    "client_id" to clientId,
    "response_type" to "code",
    "response_mode" to "form_post",
    "redirect_uri" to attempt.redirectUri,
    "scope" to scopes.joinToString(" "),
    "state" to attempt.state,
    "code_challenge" to attempt.pkce.challenge,
    "code_challenge_method" to PkcePair.METHOD,
)

/**
 * The URL to hand to the system browser once the PAR call has returned its `request_uri`.
 *
 * Deliberately minimal: repeating the parameters here would undo what PAR just bought, and
 * Authelia ignores them anyway once a `request_uri` is present.
 */
fun authorizationUrl(endpoints: EngineOAuthEndpoints, clientId: String, requestUri: String): String =
    URLBuilder(Url(endpoints.authorizationEndpoint)).apply {
        parameters.append("client_id", clientId)
        parameters.append("request_uri", requestUri)
    }.buildString()

/** Le chemin de la route qui reçoit le `form_post` côté serveur. */
const val CALLBACK_PATH: String = "/oauth/authelia"

/**
 * L'adresse à laquelle le portail renvoie le code : une route du planificateur, en HTTPS.
 *
 * Pas une boucle locale — le motif venait du bureau et supposait que l'application tourne encore
 * pendant que la personne est dans son navigateur, ce qu'Android ne garantit pas (D-049). Pas un
 * schéma d'application non plus, directement : `form_post` est imposé par le scope
 * `authelia.bearer.authz`, et une App Link perd le corps de la requête. Le serveur reçoit donc le
 * POST, et rebondit vers le schéma en GET.
 *
 * Construite à partir de l'adresse du planificateur plutôt qu'écrite en dur : c'est le même
 * raisonnement que [EngineAccess][com.garfiec.librechat.core.network.engine.EngineAccess] — un
 * déploiement qui n'est pas celui-ci existe, et une constante compilée le rendrait impossible.
 *
 * Rend `null` quand il n'y a pas d'adresse utilisable. Ce n'est pas une erreur en soi : le
 * planificateur est facultatif partout ailleurs. Mais sans lui il n'y a **pas de porte de retour**,
 * donc pas de connexion possible, et l'appelant doit le dire plutôt que de fabriquer une URL que le
 * portail rejettera avec un message qui ne parle de rien.
 */
fun callbackRedirectUri(schedulerUrl: String, path: String = CALLBACK_PATH): String? {
    val base = schedulerUrl.trim().trimEnd('/')
    if (base.isBlank()) return null
    // Un hôte nu (« sched.example.com ») produirait un `redirect_uri` sans schéma, qu'Authelia
    // refuse en `invalid_request` — un message qui ne nomme ni le réglage ni sa valeur.
    if (!base.startsWith("http://", ignoreCase = true) &&
        !base.startsWith("https://", ignoreCase = true)
    ) {
        return null
    }
    return base + path
}

@Serializable
data class PushedAuthorizationResponse(
    @SerialName("request_uri") val requestUri: String,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

@Serializable
data class EngineTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String? = null,
)
