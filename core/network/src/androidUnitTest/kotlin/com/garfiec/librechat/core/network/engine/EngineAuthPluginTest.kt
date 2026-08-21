package com.garfiec.librechat.core.network.engine

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The two credentials, and what happens when the proxy says no. Every behaviour here was observed
 * on the real chain — Authelia redirects rather than answering 401, and `Proxy-Authorization` is
 * hop-by-hop and gets eaten unless the edge puts it back.
 */
class EngineAuthPluginTest {

    private val engineAccess = EngineAccess(
        baseUrl = "https://agent.example.com",
        issuerUrl = "https://auth.example.com",
        clientId = "hobbitton-chat-android",
        username = "opencode",
        password = "engine-secret",
    )

    private fun client(
        engine: MockEngine,
        bearer: String? = "bearer-1",
        renew: suspend () -> String? = { null },
    ) = HttpClient(engine) {
        install(EngineAuthPlugin) {
            this.access = { engineAccess }
            this.bearer = { bearer }
            this.renew = renew
        }
    }

    @Test
    fun `the engine gets its Basic and the proxy gets the bearer`() = runTest {
        var authorization: String? = null
        var proxyAuthorization: String? = null
        val engine = MockEngine { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            proxyAuthorization = request.headers[HttpHeaders.ProxyAuthorization]
            respond("ok", HttpStatusCode.OK)
        }

        client(engine).get("https://agent.example.com/doc")

        // The engine refuses anything but its own Basic…
        assertThat(authorization).startsWith("Basic ")
        // …and the proxy reads the bearer from the other header. Swapping them locks out one gate
        // or the other, with an error that names neither.
        assertThat(proxyAuthorization).isEqualTo("Bearer bearer-1")
    }

    @Test
    fun `a portal redirect counts as a refusal, even though it is a 302`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(
                        HttpHeaders.Location,
                        "https://auth.example.com/?rd=https%3A%2F%2Fagent.example.com%2Fdoc",
                    ),
                )
            } else {
                respond("ok", HttpStatusCode.OK)
            }
        }

        val response = client(engine, renew = { "bearer-2" }).get("https://agent.example.com/doc")

        // Without this, the client follows the redirect, gets the login page with status 200, and
        // hands HTML to a JSON parser. The error names neither authentication nor the portal.
        assertThat(calls).isEqualTo(2)
        assertThat(response.bodyAsText()).isEqualTo("ok")
    }

    @Test
    fun `the retry carries the renewed bearer, not the stale one`() = runTest {
        val seen = mutableListOf<String?>()
        val engine = MockEngine { request ->
            seen += request.headers[HttpHeaders.ProxyAuthorization]
            if (seen.size == 1) respond("", HttpStatusCode.Unauthorized) else respond("ok", HttpStatusCode.OK)
        }

        client(engine, renew = { "bearer-2" }).get("https://agent.example.com/doc")

        assertThat(seen).containsExactly("Bearer bearer-1", "Bearer bearer-2").inOrder()
    }

    @Test
    fun `renewal is attempted once, never in a loop`() = runTest {
        var renewals = 0
        var calls = 0
        val engine = MockEngine {
            calls++
            respond("", HttpStatusCode.Unauthorized)
        }

        client(engine, renew = { renewals++; "bearer-2" }).get("https://agent.example.com/doc")

        // A bearer that is refused twice means the portal session is gone. Spinning here would turn
        // « log in again » into a silent hammering of the server.
        assertThat(renewals).isEqualTo(1)
        assertThat(calls).isEqualTo(2)
    }

    @Test
    fun `a failed renewal returns the refusal instead of pretending`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }

        val response = client(engine, renew = { null }).get("https://agent.example.com/doc")

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `a redirect somewhere other than the portal is left alone`() = runTest {
        var renewals = 0
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "https://agent.example.com/elsewhere"),
                )
            } else {
                respond("ok", HttpStatusCode.OK)
            }
        }

        client(engine, renew = { renewals++; "bearer-2" }).get("https://agent.example.com/doc")

        // An ordinary redirect is not an authorization problem — Ktor follows it and that is the
        // end of it. Renewing here would hide real routing behaviour behind a token dance.
        assertThat(renewals).isEqualTo(0)
    }

    @Test
    fun `no bearer yet means the Basic still goes, and the portal decides`() = runTest {
        var proxyAuthorization: String? = "sentinel"
        val engine = MockEngine { request ->
            proxyAuthorization = request.headers[HttpHeaders.ProxyAuthorization]
            respond("ok", HttpStatusCode.OK)
        }

        client(engine, bearer = null).get("https://agent.example.com/doc")

        // Sending `Bearer null` would be worse than sending nothing: the proxy would reject a
        // malformed credential rather than treat the request as anonymous.
        assertThat(proxyAuthorization).isNull()
    }
}
