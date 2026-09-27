package com.garfiec.librechat.core.data.portal

import com.garfiec.librechat.core.network.di.librechatJson
import com.garfiec.librechat.core.network.engine.EngineAccess
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import com.garfiec.librechat.core.network.engine.EngineTokens
import com.garfiec.librechat.core.network.engine.auth.EngineOAuthEndpoints
import com.garfiec.librechat.core.network.engine.auth.EngineTokenClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Signing out of the portal (D-076): one login opens the chat and the tasks, so one logout must
 * close both — the portal's tokens and the web view's cookies on the hosts the login went through.
 */
class PortalSignOutTest {

    private val access = EngineAccess(
        baseUrl = "https://agent.example.com",
        issuerUrl = "https://auth.example.com",
        schedulerUrl = "https://sched.example.com/",
    )

    private class FakeStore(var tokens: EngineTokens?) : EngineTokenStore {
        override suspend fun read(): EngineTokens? = tokens
        override suspend fun write(tokens: EngineTokens) {
            this.tokens = tokens
        }

        override suspend fun clear() {
            tokens = null
        }
    }

    private class RecordingJar : WebCookieJar {
        val expired = mutableListOf<List<String>>()
        override suspend fun expire(origins: List<String>) {
            expired += origins
        }
    }

    private fun session(store: FakeStore) = PortalSession(
        store = store,
        client = EngineTokenClient(
            HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) }) {
                install(ContentNegotiation) { json(librechatJson) }
            },
        ),
        endpoints = {
            EngineOAuthEndpoints(
                issuer = "https://auth.example.com",
                authorizationEndpoint = "https://auth.example.com/authorize",
                tokenEndpoint = "https://auth.example.com/token",
            )
        },
        now = { 1_000 },
    )

    @Test
    fun `signing out drops the portal's tokens and expires the four hosts' cookies`() = runTest {
        val store = FakeStore(EngineTokens("at-1", "rt-1", expiresAtEpochSeconds = 9_999))
        val jar = RecordingJar()

        PortalSignOut(session(store), access = { access }, cookies = jar)
            .onSignedOut("https://chat.example.com")

        assertNull(store.tokens)
        assertEquals(
            listOf(
                listOf(
                    "https://chat.example.com",
                    "https://auth.example.com",
                    "https://agent.example.com",
                    "https://sched.example.com",
                ),
            ),
            jar.expired,
        )
    }

    @Test
    fun `without engine settings the chat's cookies still go`() = runTest {
        // A phone that never set the tasks up still went through the portal to reach the chat.
        val jar = RecordingJar()

        PortalSignOut(session(FakeStore(null)), access = { null }, cookies = jar)
            .onSignedOut("https://chat.example.com")

        assertEquals(listOf(listOf("https://chat.example.com")), jar.expired)
    }

    @Test
    fun `origins are normalised, deduplicated, and never invented`() {
        val origins = portalOrigins(
            serverUrl = "HTTPS://Chat.Example.com/c/new",
            access = EngineAccess(
                baseUrl = "https://chat.example.com:443",
                issuerUrl = "auth.example.com",
                schedulerUrl = "",
            ),
        )

        // Same origin twice collapses; a schemeless or blank address is dropped rather than guessed.
        assertEquals(listOf("https://chat.example.com"), origins)
        assertEquals(listOf("http://127.0.0.1:4096"), portalOrigins("http://127.0.0.1:4096/", null))
        assertTrue(portalOrigins(null, null).isEmpty())
    }

    @Test
    fun `each cookie is expired host-only and on every parent domain`() {
        // The portal's session cookie lives on the registrable domain, shared by the four hosts;
        // LibreChat's are host-only. The jar says neither, so both are written.
        val writes = expiringCookies("authelia_session=abc; refreshToken=def", "auth.example.com", secure = true)

        val suffix = "; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Secure"
        assertEquals(
            listOf(
                "authelia_session=$suffix",
                "authelia_session=$suffix; Domain=auth.example.com",
                "authelia_session=$suffix; Domain=example.com",
                "refreshToken=$suffix",
                "refreshToken=$suffix; Domain=auth.example.com",
                "refreshToken=$suffix; Domain=example.com",
            ),
            writes,
        )
    }

    @Test
    fun `nothing to expire means nothing is written`() {
        assertTrue(expiringCookies(null, "auth.example.com", secure = true).isEmpty())
        assertTrue(expiringCookies("  ;  ", "auth.example.com", secure = true).isEmpty())
    }

    @Test
    fun `a cleartext private origin gets no Secure attribute`() {
        val writes = expiringCookies("sid=1", "192.168.1.20", secure = false)

        assertEquals(listOf("sid=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT"), writes)
    }

    @Test
    fun `parent domains stop before a single label, and addresses have none`() {
        assertEquals(listOf("a.b.example.com", "b.example.com", "example.com"), parentDomains("a.b.example.com"))
        assertEquals(listOf("example.com"), parentDomains("Example.COM"))
        assertTrue(parentDomains("localhost").isEmpty())
        assertTrue(parentDomains("10.0.0.2").isEmpty())
        assertTrue(parentDomains("[fd00::1]").isEmpty())
        assertTrue(parentDomains("fd00::1").isEmpty())
    }
}
