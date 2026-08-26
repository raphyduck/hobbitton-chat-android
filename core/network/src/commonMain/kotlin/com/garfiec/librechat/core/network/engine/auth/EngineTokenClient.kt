package com.garfiec.librechat.core.network.engine.auth

import com.garfiec.librechat.core.common.result.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters

/**
 * The two back-channel calls of the flow, plus discovery.
 *
 * Everything here is form-encoded, never JSON: OAuth's token endpoint is `application/x-www-form-
 * urlencoded` by specification, and Authelia rejects a JSON body with an error that names neither
 * the format nor the field.
 *
 * No client secret anywhere. This is a *public* client — an installed app cannot keep one — and the
 * proof of possession is the PKCE verifier, which is why it travels here and only here.
 */
class EngineTokenClient(
    private val client: HttpClient,
    private val clientId: String,
) {

    /** Reads the endpoints from the issuer rather than assuming Authelia's paths. */
    suspend fun discover(issuerBaseUrl: String): EngineOAuthEndpoints =
        client.get("${issuerBaseUrl.trimEnd('/')}/.well-known/openid-configuration").body()

    /**
     * Pushes the authorization request and returns the opaque handle to put in front of the user's
     * browser. Fails loudly when the server advertises no PAR endpoint: falling back to a plain
     * authorize URL would silently put scopes, challenge and state back into browser history.
     */
    suspend fun pushAuthorizationRequest(
        endpoints: EngineOAuthEndpoints,
        attempt: EngineAuthorizationAttempt,
        scopes: List<String> = EngineScopes.DEFAULT,
        audiences: List<String> = emptyList(),
    ): PushedAuthorizationResponse {
        val endpoint = requireNotNull(endpoints.parEndpoint) {
            "This Authelia does not advertise a pushed authorization request endpoint"
        }
        val form = Parameters.build {
            pushedAuthorizationForm(clientId, attempt, scopes, audiences)
                .forEach { (k, v) -> append(k, v) }
        }
        return client.submitForm(url = endpoint, formParameters = form).body()
    }

    /**
     * Swaps the authorization code for tokens.
     *
     * `redirect_uri` is repeated even though the code already carries it: the server compares them,
     * and a mismatch — the loopback port differing by one exchange — is rejected as
     * `invalid_grant`, which reads like an expired code and is not one.
     */
    suspend fun exchangeCode(
        endpoints: EngineOAuthEndpoints,
        code: String,
        attempt: EngineAuthorizationAttempt,
    ): EngineTokenResponse = client.submitForm(
        url = endpoints.tokenEndpoint,
        formParameters = Parameters.build {
            append("grant_type", "authorization_code")
            append("code", code)
            append("redirect_uri", attempt.redirectUri)
            append("client_id", clientId)
            append("code_verifier", attempt.pkce.verifier)
        },
    ).body()

    /**
     * Renews the access token without sending the user back through the portal.
     *
     * The response may carry a **new** refresh token; callers must store whichever came back rather
     * than keeping the old one, or the next renewal fails once the server rotates them.
     *
     * Raises [EngineGrantRefused] — and only then — when the portal rejects the grant itself. Every
     * other failure (no network, a proxy hiccup, a portal being restarted) comes out as whatever it
     * was, so the caller can keep a session that is still perfectly good. Both used to arrive as an
     * indistinguishable `Throwable`, and the caller forgot the session on either.
     */
    suspend fun refresh(
        endpoints: EngineOAuthEndpoints,
        refreshToken: String,
    ): EngineTokenResponse {
        val response = try {
            client.submitForm(
                url = endpoints.tokenEndpoint,
                formParameters = Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                    append("client_id", clientId)
                },
            )
        } catch (raised: ApiException) {
            // This client validates responses, so a failing status arrives here rather than below.
            // A 5xx is deliberately left alone: an overloaded portal is not a revoked token.
            if (raised.statusCode in REFUSAL_STATUSES) {
                throw EngineGrantRefused(
                    error = oauthError(raised.body) ?: "http ${raised.statusCode}",
                    cause = raised,
                )
            }
            throw raised
        }
        // And when it does not validate — the shape the tests use, and any future client without a
        // validator — the same status has to be read off the response itself. Left to fall through,
        // a `{"error":"invalid_grant"}` body would surface as a deserialization failure: a
        // definitive refusal wearing the costume of a transient one.
        if (response.status.value in REFUSAL_STATUSES) {
            val body = runCatching { response.bodyAsText() }.getOrNull()
            throw EngineGrantRefused(oauthError(body) ?: "http ${response.status.value}")
        }
        return response.body()
    }

    private companion object {
        /**
         * The portal's own refusals. `400` carries `invalid_grant`, `401` an unknown client — both
         * are final, and no amount of retrying turns them into a token.
         */
        val REFUSAL_STATUSES = 400..499

        /** Best effort: the log reads better with `invalid_grant` than with `http 400`. */
        fun oauthError(body: String?): String? = body
            ?.substringAfter("\"error\":\"", "")
            ?.substringBefore('"')
            ?.takeIf { it.isNotBlank() }
    }
}
