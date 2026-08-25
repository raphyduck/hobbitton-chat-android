package com.garfiec.librechat.core.network.engine.auth

import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parseUrlEncodedParameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The flow that gets the app past Authelia. The constraints encoded here were measured on the real
 * chain on 20–21/08/2026, not read off a tutorial.
 */
class EngineAuthorizationTest {

    private val endpoints = EngineOAuthEndpoints(
        issuer = "https://auth.example.com",
        authorizationEndpoint = "https://auth.example.com/api/oidc/authorization",
        tokenEndpoint = "https://auth.example.com/api/oidc/token",
        parEndpoint = "https://auth.example.com/api/oidc/pushed-authorization-request",
    )

    private val attempt = EngineAuthorizationAttempt(
        pkce = PkcePair(verifier = "verifier-secret", challenge = "challenge-digest"),
        state = "state-123",
        redirectUri = callbackRedirectUri("https://sched.example.com")!!,
    )

    private fun client(engine: MockEngine) = EngineTokenClient(
        HttpClient(engine) { install(ContentNegotiation) { json(librechatJson) } },
        clientId = "hobbitton-chat-android",
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `la redirection est une route HTTPS du serveur, pas un schema d'application`() {
        // Un `form_post` ne peut pas être délivré à `at.hobbitton.chat://…` : une App Link perd le
        // corps. C'est donc le serveur qui le reçoit, et qui rebondit vers le schéma en GET (D-049).
        assertThat(attempt.redirectUri).isEqualTo("https://sched.example.com/oauth/authelia")
    }

    @Test
    fun `une barre finale dans le reglage ne double pas la barre de la route`() {
        // `redirect_uri` est comparé caractère pour caractère par le portail, entre la demande et
        // l'échange, et contre ce qui est enregistré. Un `//oauth/authelia` échoue en
        // `invalid_request`, un message qui ne nomme ni le réglage ni sa valeur.
        assertThat(callbackRedirectUri("https://sched.example.com/"))
            .isEqualTo("https://sched.example.com/oauth/authelia")
    }

    @Test
    fun `sans adresse de planificateur il n'y a pas de porte de retour`() {
        assertThat(callbackRedirectUri("")).isNull()
        assertThat(callbackRedirectUri("   ")).isNull()
    }

    @Test
    fun `un hote nu sans schema est refuse ici plutot que par le portail`() {
        // Authelia le rejetterait en `invalid_request`, sans dire quel réglage est en cause.
        assertThat(callbackRedirectUri("sched.example.com")).isNull()
    }

    @Test
    fun `la demande reclame les audiences, sans quoi le jeton n'ouvre rien`() {
        // Le defaut du 25/08. Tout le tour reussissait — portail, code, echange — et chaque requete
        // repartait en `invalid_target` : « token does not contain a valid audience ». La liste
        // `audience` du client cote portail dit ce qui est PERMIS, pas ce qui est accorde.
        val form = pushedAuthorizationForm(
            "hobbitton-chat-android",
            attempt,
            audiences = listOf("https://agent.example.com", "https://sched.example.com"),
        ).toMap()

        assertThat(form["audience"])
            .isEqualTo("https://agent.example.com https://sched.example.com")
    }

    @Test
    fun `une barre finale ne fait pas rater l'appariement d'audience`() {
        // Les valeurs sont comparees telles quelles a la liste du client. Un `/` de trop suffit a
        // faire refuser le jeton, avec un message qui ne nomme ni le reglage ni sa valeur.
        val form = pushedAuthorizationForm(
            "hobbitton-chat-android",
            attempt,
            audiences = listOf("https://agent.example.com/", "  https://sched.example.com  "),
        ).toMap()

        assertThat(form["audience"])
            .isEqualTo("https://agent.example.com https://sched.example.com")
    }

    @Test
    fun `une audience vide n'est pas envoyee du tout`() {
        // Le planificateur est facultatif : son adresse peut etre vide. Envoyer un `audience` vide
        // ou avec une entree blanche ferait rejeter la demande entiere plutot que d'en retirer une
        // cible.
        val form = pushedAuthorizationForm(
            "hobbitton-chat-android",
            attempt,
            audiences = listOf("https://agent.example.com", "", "   "),
        ).toMap()

        assertThat(form["audience"]).isEqualTo("https://agent.example.com")
        assertThat(pushedAuthorizationForm("hobbitton-chat-android", attempt).toMap())
            .doesNotContainKey("audience")
    }

    @Test
    fun `the pushed request asks for form_post and the bearer scope`() {
        val form = pushedAuthorizationForm("hobbitton-chat-android", attempt).toMap()

        // Authelia's validate-config refuses `authelia.bearer.authz` with any other response mode.
        assertThat(form["response_mode"]).isEqualTo("form_post")
        // Without this scope the token is an ordinary OIDC one and forward-auth still redirects.
        assertThat(form["scope"]).contains(EngineScopes.BEARER_AUTHZ)
        // Without offline_access, every expiry costs a full portal round trip, 2FA included.
        assertThat(form["scope"]).contains(EngineScopes.OFFLINE)
        assertThat(form["code_challenge_method"]).isEqualTo("S256")
        assertThat(form["code_challenge"]).isEqualTo("challenge-digest")
        // The secret never travels on the front channel.
        assertThat(form.values).doesNotContain("verifier-secret")
    }

    @Test
    fun `the browser URL carries only the opaque handle`() {
        val url = authorizationUrl(endpoints, "hobbitton-chat-android", "urn:ietf:params:oauth:request_uri:xyz")

        assertThat(url).contains("request_uri=urn")
        // The point of PAR: nothing else lands in browser history or in a proxy log.
        assertThat(url).doesNotContain("code_challenge")
        assertThat(url).doesNotContain("scope")
        assertThat(url).doesNotContain("state")
    }

    @Test
    fun `the code exchange sends the verifier and repeats the redirect`() = runTest {
        var body: String? = null
        val engine = MockEngine { request ->
            body = String(request.body.toByteArray())
            respond(
                content = """{"access_token":"at-1","token_type":"bearer","expires_in":3600,
                    |"refresh_token":"rt-1"}""".trimMargin(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val token = client(engine).exchangeCode(endpoints, code = "code-abc", attempt = attempt)

        val fields = body!!.parseUrlEncodedParameters()
        assertThat(fields["grant_type"]).isEqualTo("authorization_code")
        assertThat(fields["code_verifier"]).isEqualTo("verifier-secret")
        // Repeated on purpose: the server compares it between the two calls, character for
        // character, and a divergence comes back as `invalid_grant` — which reads exactly like an
        // expired code and is not one.
        assertThat(fields["redirect_uri"]).isEqualTo("https://sched.example.com/oauth/authelia")
        // Public client: there is no secret to send, and sending one would be a different flow.
        assertThat(fields["client_secret"]).isNull()
        assertThat(token.accessToken).isEqualTo("at-1")
        assertThat(token.refreshToken).isEqualTo("rt-1")
    }

    @Test
    fun `a refresh may rotate the refresh token`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"access_token":"at-2","refresh_token":"rt-2","expires_in":3600}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val token = client(engine).refresh(endpoints, refreshToken = "rt-1")

        // Keeping rt-1 would work until the server rotates, then fail with no obvious cause.
        assertThat(token.refreshToken).isEqualTo("rt-2")
    }

    @Test
    fun `pushing without a PAR endpoint fails loudly instead of falling back`() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.OK, jsonHeaders()) }
        val withoutPar = endpoints.copy(parEndpoint = null)

        val error = runCatching {
            client(engine).pushAuthorizationRequest(withoutPar, attempt)
        }.exceptionOrNull()

        // Falling back to a plain authorize URL would put scope, challenge and state back into
        // browser history — silently. Better to stop.
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }
}
