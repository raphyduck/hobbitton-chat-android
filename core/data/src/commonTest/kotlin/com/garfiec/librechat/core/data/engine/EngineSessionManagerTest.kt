package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.network.di.librechatJson
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import com.garfiec.librechat.core.network.engine.EngineTokens
import com.garfiec.librechat.core.network.engine.auth.EngineOAuthEndpoints
import com.garfiec.librechat.core.network.engine.auth.EngineTokenClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Renewal of the engine's bearer. Every case here is one where getting it wrong logs the user out
 * of a session that was perfectly alive — the failure mode that is hardest to notice, because the
 * app looks like it is behaving correctly.
 */
class EngineSessionManagerTest {

    private class FakeStore(var tokens: EngineTokens?) : EngineTokenStore {
        var cleared = false
        override suspend fun read(): EngineTokens? = tokens
        override suspend fun write(tokens: EngineTokens) {
            this.tokens = tokens
        }

        override suspend fun clear() {
            tokens = null
            cleared = true
        }
    }

    private val endpoints = EngineOAuthEndpoints(
        issuer = "https://auth.example.com",
        authorizationEndpoint = "https://auth.example.com/authorize",
        tokenEndpoint = "https://auth.example.com/token",
        parEndpoint = "https://auth.example.com/par",
    )

    private fun manager(
        store: FakeStore,
        engine: MockEngine,
        now: () -> Long = { 1_000 },
    ) = EngineSessionManager(
        store = store,
        client = EngineTokenClient(
            HttpClient(engine) { install(ContentNegotiation) { json(librechatJson) } },
            clientId = "hobbitton-chat-android",
        ),
        endpoints = { endpoints },
        now = now,
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `a token still valid is used as is, without a network call`() = runTest {
        var calls = 0
        val engine = MockEngine { calls++; respond("{}", HttpStatusCode.OK, jsonHeaders()) }
        val store = FakeStore(EngineTokens("at-1", "rt-1", expiresAtEpochSeconds = 9_999))

        val bearer = manager(store, engine).bearer()

        assertEquals("at-1", bearer)
        assertEquals(0, calls)
    }

    @Test
    fun `an expired token is renewed before use`() = runTest {
        val engine = MockEngine {
            respond(
                """{"access_token":"at-2","refresh_token":"rt-2","expires_in":3600}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }
        val store = FakeStore(EngineTokens("at-1", "rt-1", expiresAtEpochSeconds = 500))

        val bearer = manager(store, engine).bearer()

        assertEquals("at-2", bearer)
        assertEquals("rt-2", store.tokens?.refreshToken)
        // The expiry is stored as an instant, not a duration: the app is suspended and resumed at
        // the OS's convenience, and a duration means nothing without the moment it was measured at.
        assertEquals(1_000 + 3_600, store.tokens?.expiresAtEpochSeconds)
    }

    @Test
    fun `a server that does not rotate keeps the refresh token we already hold`() = runTest {
        // The response carries no refresh_token. Dropping ours would work now and fail at the
        // *next* renewal — far from the change that caused it.
        val engine = MockEngine {
            respond("""{"access_token":"at-2","expires_in":3600}""", HttpStatusCode.OK, jsonHeaders())
        }
        val store = FakeStore(EngineTokens("at-1", "rt-1", expiresAtEpochSeconds = 500))

        manager(store, engine).bearer()

        assertEquals("rt-1", store.tokens?.refreshToken)
    }

    @Test
    fun `a refused renewal forgets the pair instead of retrying it forever`() = runTest {
        val engine = MockEngine {
            respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest, jsonHeaders())
        }
        val store = FakeStore(EngineTokens("at-1", "rt-1", expiresAtEpochSeconds = 500))

        val bearer = manager(store, engine).bearer()

        assertNull(bearer)
        assertEquals(true, store.cleared)
    }

    @Test
    fun `a portal that cannot be reached keeps the session it could not renew`() = runTest {
        // The one that used to log the user out for a lost second of Wi-Fi. The tokens are still
        // perfectly valid; only the trip to the portal failed. Forgetting them here costs a full
        // second-factor login, and nothing on screen says why.
        val engine = MockEngine { throw IOException("network is unreachable") }
        val store = FakeStore(EngineTokens("at-1", "rt-1", expiresAtEpochSeconds = 500))

        val bearer = manager(store, engine).bearer()

        assertNull(bearer)
        assertEquals(false, store.cleared)
        assertEquals("rt-1", store.tokens?.refreshToken)
    }

    @Test
    fun `a portal that is merely overloaded is not a revoked session either`() = runTest {
        // 5xx is the portal having a bad moment, not the grant being dead. Same reasoning as the
        // unreachable case, and the status is the only thing that tells them apart.
        val engine = MockEngine { respond("upstream is down", HttpStatusCode.BadGateway) }
        val store = FakeStore(EngineTokens("at-1", "rt-1", expiresAtEpochSeconds = 500))

        assertNull(manager(store, engine).bearer())
        assertEquals(false, store.cleared)
    }

    @Test
    fun `no refresh token at all means the portal has to be visited again`() = runTest {
        var calls = 0
        val engine = MockEngine { calls++; respond("{}", HttpStatusCode.OK, jsonHeaders()) }
        val store = FakeStore(EngineTokens("at-1", refreshToken = null, expiresAtEpochSeconds = 500))

        assertNull(manager(store, engine).bearer())
        // Nothing to renew with — posting anyway would just produce a doomed round trip.
        assertEquals(0, calls)
    }

    @Test
    fun `concurrent callers renew once, and all get the new token`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(
                """{"access_token":"at-2","refresh_token":"rt-2","expires_in":3600}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }
        val store = FakeStore(EngineTokens("at-1", "rt-1", expiresAtEpochSeconds = 500))
        val subject = manager(store, engine)

        val bearers = List(5) { async { subject.renew() } }.awaitAll()

        // Five refreshes with the same token means the server rotates on the first and answers
        // invalid_grant to the other four — and the app logs the user out on a session it had just
        // successfully renewed.
        assertEquals(1, calls)
        assertEquals(List(5) { "at-2" }, bearers)
    }

    @Test
    fun `an empty store is not a session to renew`() = runTest {
        var calls = 0
        val engine = MockEngine { calls++; respond("{}", HttpStatusCode.OK, jsonHeaders()) }
        val store = FakeStore(null)

        assertNull(manager(store, engine).bearer())
        assertEquals(0, calls)
    }

    @Test
    fun `a token without an expiry is taken at face value`() = runTest {
        // Some issuers omit expires_in. Treating "unknown" as "expired" would renew on every single
        // request, which is both wasteful and a good way to hit a rate limit.
        var calls = 0
        val engine = MockEngine { calls++; respond("{}", HttpStatusCode.OK, jsonHeaders()) }
        val store = FakeStore(EngineTokens("at-1", "rt-1", expiresAtEpochSeconds = null))

        assertEquals("at-1", manager(store, engine).bearer())
        assertEquals(0, calls)
    }
}
