package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.network.engine.EngineAccess
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import com.garfiec.librechat.core.network.engine.EngineTokens
import com.garfiec.librechat.core.network.engine.auth.EngineOAuthEndpoints
import com.garfiec.librechat.core.network.engine.auth.EngineTokenClient
import com.garfiec.librechat.core.network.engine.auth.FormPostCallback
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parseUrlEncodedParameters
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/**
 * The round trip that had no caller.
 *
 * These tests exist because the gap they cover was invisible to every test that already passed:
 * PKCE, the PAR form, the callback parser and the code exchange each had a green suite of their own,
 * and the application still could not sign in even once — nothing joined them up. A test of a part
 * proves the part; only a test of the whole proves the whole is wired.
 */
class EngineSignInTest {

    private class FakeStore : EngineTokenStore {
        var stored: EngineTokens? = null
        override suspend fun read(): EngineTokens? = stored
        override suspend fun write(tokens: EngineTokens) { stored = tokens }
        override suspend fun clear() { stored = null }
    }

    /**
     * La boîte aux lettres, remplacée par ce qu'elle aurait reçu.
     *
     * [answer] prend le `state` relevé dans le formulaire PAR, pour qu'un test puisse répondre avec
     * celui-là même que le flux vient d'envoyer — ce que fait un portail, et ce qui donne son sens
     * au test de discordance.
     */
    private class FakeInbox(
        val answer: (state: String) -> FormPostCallback,
    ) : EngineCallbackInbox {
        var armed = false
        var released = false
        lateinit var stateSeen: () -> String
        override fun armer() { armed = true }
        override suspend fun attendre(timeoutMillis: Long): FormPostCallback = answer(stateSeen())
        override fun liberer() { released = true }
    }

    private val endpoints = EngineOAuthEndpoints(
        issuer = "https://auth.example.com",
        authorizationEndpoint = "https://auth.example.com/api/oidc/authorization",
        tokenEndpoint = "https://auth.example.com/api/oidc/token",
        parEndpoint = "https://auth.example.com/api/oidc/pushed-authorization-request",
    )

    private val access = EngineAccess(
        baseUrl = "https://agent.example.com",
        issuerUrl = "https://auth.example.com",
        clientId = "hobbitton-chat-android",
        username = "opencode",
        password = "motdepasse",
        schedulerUrl = "https://sched.example.com",
    )

    /** Records what each back-channel call carried — where the wiring mistakes actually live. */
    private class Portal(private val grantedScope: String? = "authelia.bearer.authz offline_access") {
        val forms = mutableMapOf<String, Map<String, String>>()

        fun client(): EngineTokenClient {
            val engine = MockEngine { request ->
                val fields = request.body.toByteArray().decodeToString()
                    .parseUrlEncodedParameters()
                    .entries()
                    .associate { (name, values) -> name to values.first() }
                if (request.url.toString().endsWith("pushed-authorization-request")) {
                    forms["par"] = fields
                    respond(
                        """{"request_uri":"urn:ietf:params:oauth:request_uri:xyz","expires_in":60}""",
                        HttpStatusCode.OK,
                        headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                } else {
                    forms["token"] = fields
                    val scope = grantedScope?.let { ""","scope":"$it"""" }.orEmpty()
                    respond(
                        """{"access_token":"jeton","refresh_token":"renouveau","expires_in":3600$scope}""",
                        HttpStatusCode.OK,
                        headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            }
            return EngineTokenClient(
                client = HttpClient(engine) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                },
                clientId = "hobbitton-chat-android",
            )
        }
    }

    private fun flow(
        portal: Portal,
        inbox: FakeInbox,
        store: FakeStore,
        engine: EngineAccess? = access,
    ): EngineSignIn {
        inbox.stateSeen = { portal.forms["par"]?.get("state").orEmpty() }
        val tokens = portal.client()
        return EngineSignIn(
            access = { engine },
            tokens = tokens,
            sessions = EngineSessionManager(
                store = store,
                client = tokens,
                endpoints = { endpoints },
                now = { 1_000 },
            ),
            inbox = inbox,
            endpoints = { endpoints },
        )
    }

    @Test
    fun `un aller-retour complet stocke les jetons`() = runTest {
        val portal = Portal()
        val store = FakeStore()
        val inbox = FakeInbox { state -> FormPostCallback.Success("le-code", state) }

        var opened: String? = null
        val result = flow(portal, inbox, store).signIn { opened = it }

        assertEquals(EngineSignInResult.Authorized, result)
        assertEquals("jeton", store.stored?.accessToken)
        assertEquals("renouveau", store.stored?.refreshToken)
        assertTrue(inbox.armed && inbox.released, "la boîte s'arme puis se libère")
        assertTrue(opened!!.contains("request_uri="), "le navigateur reçoit l'URL opaque du PAR")
        assertTrue(
            "code_challenge" !in opened!!,
            "PAR existe justement pour que le défi ne traverse pas le navigateur",
        )
    }

    @Test
    fun `la redirection annoncee est la route HTTPS, la meme aux deux etapes`() = runTest {
        // Le portail compare `redirect_uri` entre la demande et l'échange, caractère pour
        // caractère. Une divergence est refusée en `invalid_grant`, ce qui se lit comme un code
        // expiré et n'en est pas un.
        val portal = Portal()
        val inbox = FakeInbox { state -> FormPostCallback.Success("le-code", state) }

        flow(portal, inbox, FakeStore()).signIn { }

        val attendu = "https://sched.example.com/oauth/authelia"
        assertEquals(attendu, portal.forms["par"]?.get("redirect_uri"))
        assertEquals(attendu, portal.forms["token"]?.get("redirect_uri"))
    }

    @Test
    fun `sans adresse de planificateur, la connexion s'arrete avant le portail`() = runTest {
        // La route de retour vit sur le planificateur : sans son adresse, le portail n'a nulle part
        // où renvoyer le code. Son propre résultat, parce que sa réparation lui est propre — il ne
        // manque pas « les réglages », il manque celui-là.
        val portal = Portal()
        val inbox = FakeInbox { FormPostCallback.Malformed("jamais appelé") }

        var opened = false
        val result = flow(portal, inbox, FakeStore(), engine = access.copy(schedulerUrl = ""))
            .signIn { opened = true }

        assertEquals(EngineSignInResult.NoCallbackHost, result)
        assertTrue(!opened, "aucun navigateur ne doit s'ouvrir")
        assertTrue(!inbox.armed, "rien ne doit être armé")
    }

    @Test
    fun `le verificateur PKCE ne part qu'au canal arriere`() = runTest {
        val portal = Portal()
        val inbox = FakeInbox { state -> FormPostCallback.Success("le-code", state) }

        flow(portal, inbox, FakeStore()).signIn { }

        val defi = portal.forms["par"]?.get("code_challenge")
        val verificateur = portal.forms["token"]?.get("code_verifier")
        assertTrue(!defi.isNullOrBlank() && !verificateur.isNullOrBlank())
        assertTrue(defi != verificateur, "le défi est le condensé, pas le secret")
        assertEquals("S256", portal.forms["par"]?.get("code_challenge_method"))
    }

    @Test
    fun `un state qui n'est pas le notre ne depense pas le code`() = runTest {
        val portal = Portal()
        val store = FakeStore()
        val inbox = FakeInbox { FormPostCallback.Success("le-code", "un-autre-state") }

        val result = flow(portal, inbox, store).signIn { }

        assertIs<EngineSignInResult.Interrupted>(result)
        assertNull(store.stored, "rien ne doit être stocké")
        assertNull(portal.forms["token"], "le code ne doit même pas être présenté")
        assertTrue(inbox.released)
    }

    @Test
    fun `un refus du portail se distingue d'une panne`() = runTest {
        val portal = Portal()
        val inbox = FakeInbox {
            FormPostCallback.Failure("access_denied", "l'utilisateur a refusé", state = null)
        }

        val result = flow(portal, inbox, FakeStore()).signIn { }

        assertIs<EngineSignInResult.Refused>(result)
        assertEquals("access_denied", result.error)
    }

    @Test
    fun `un jeton sans la portee d'autorisation est signale, pas fete`() = runTest {
        // Le seul échec qui ressemble à une réussite : les jetons sont là, le flux est allé au
        // bout, et chaque requête continuera pourtant d'atterrir sur le portail.
        val portal = Portal(grantedScope = "openid profile")
        val store = FakeStore()
        val inbox = FakeInbox { state -> FormPostCallback.Success("le-code", state) }

        val result = flow(portal, inbox, store).signIn { }

        assertIs<EngineSignInResult.MissingAuthorizationScope>(result)
        assertEquals(listOf("openid", "profile"), result.granted)
        // Stockés quand même : ils sont valides, et les jeter n'enlèverait que la trace de ce qui
        // s'est passé.
        assertEquals("jeton", store.stored?.accessToken)
    }

    @Test
    fun `sans reglages, rien ne part vers le portail`() = runTest {
        val portal = Portal()
        val inbox = FakeInbox { FormPostCallback.Malformed("jamais appelé") }

        var opened = false
        val result = flow(portal, inbox, FakeStore(), engine = null).signIn { opened = true }

        assertEquals(EngineSignInResult.NotConfigured, result)
        assertTrue(!opened, "aucun navigateur ne doit s'ouvrir")
        assertTrue(!inbox.armed, "rien ne doit être armé")
    }

    @Test
    fun `la boite se libere meme quand le portail casse`() = runTest {
        // Une boîte laissée armée garde le `state` de la tentative morte. Le lien du tour suivant y
        // tomberait, serait rejeté au contrôle, et l'échec ne parlerait ni du portail ni de la
        // vraie cause.
        val portal = Portal()
        val inbox = FakeInbox { FormPostCallback.Malformed("le navigateur a raccroché") }

        val result = flow(portal, inbox, FakeStore()).signIn { }

        assertIs<EngineSignInResult.Interrupted>(result)
        assertTrue(inbox.released)
    }
}
