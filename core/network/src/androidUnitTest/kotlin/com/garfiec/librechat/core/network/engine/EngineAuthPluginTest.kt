package com.garfiec.librechat.core.network.engine

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
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
        schedulerUrl = "https://sched.example.com",
    )

    private fun client(
        engine: MockEngine,
        bearer: String? = "bearer-1",
        renew: suspend () -> String? = { null },
        authority: (EngineAccess) -> String = { it.baseUrl },
    ) = HttpClient(engine) {
        install(EngineAuthPlugin) {
            this.access = { engineAccess }
            this.bearer = { bearer }
            this.renew = renew
            this.authority = authority
        }
    }

    private fun HttpRequestData.credentials(): Pair<String?, String?> =
        headers[HttpHeaders.Authorization] to headers[HttpHeaders.ProxyAuthorization]

    private val noCredentials: Pair<String?, String?> = null to null

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

    // ---- Where the two credentials may go (M2, 26/09/2026) ----

    @Test
    fun `a cross-authority redirect drops both credentials`() = runTest {
        // The contract KtorRedirectContractTest pins: HttpRedirect strips Authorization and copies
        // everything else, Proxy-Authorization included — and that one is the portal's bearer,
        // which opens the engine and the scheduler and renews itself offline.
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            if (request.url.host == "agent.example.com") {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "https://evil.example.net/steal"),
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }

        client(engine).get("https://agent.example.com/doc")

        assertThat(seen).hasSize(2)
        assertThat(seen[0].credentials()).isEqualTo("Basic b3BlbmNvZGU6ZW5naW5lLXNlY3JldA==" to "Bearer bearer-1")
        assertThat(seen[1].url.host).isEqualTo("evil.example.net")
        assertThat(seen[1].credentials()).isEqualTo(noCredentials)
    }

    @Test
    fun `a redirect back into the engine's authority re-attaches both credentials`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            when {
                request.url.host == "agent.example.com" && seen.size == 1 -> respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "https://store.example.net/blob"),
                )
                request.url.host == "store.example.net" -> respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "https://agent.example.com/doc?done=1"),
                )
                else -> respond("{}", HttpStatusCode.OK)
            }
        }

        client(engine).get("https://agent.example.com/doc")

        // Stripped on the way out, back on the way home: the `State` phase runs once per call,
        // so a strip that is never undone leaves the last hop hitting the engine anonymously.
        assertThat(seen.map { it.url.host })
            .containsExactly("agent.example.com", "store.example.net", "agent.example.com").inOrder()
        assertThat(seen[1].credentials()).isEqualTo(noCredentials)
        assertThat(seen[2].credentials()).isEqualTo(seen[0].credentials())
    }

    @Test
    fun `an absolute request to another host carries no engine credentials`() = runTest {
        var credentials: Pair<String?, String?>? = null
        val engine = MockEngine { request ->
            credentials = request.credentials()
            respond("{}", HttpStatusCode.OK)
        }

        client(engine).get("https://cdn.example.net/asset")

        assertThat(credentials).isEqualTo(noCredentials)
    }

    @Test
    fun `a same-host scheme downgrade carries no engine credentials`() = runTest {
        // The engine's Basic never rotates; in the clear once is in the clear for good.
        var credentials: Pair<String?, String?>? = null
        val engine = MockEngine { request ->
            credentials = request.credentials()
            respond("{}", HttpStatusCode.OK)
        }

        client(engine).get("http://agent.example.com/doc")

        assertThat(credentials).isEqualTo(noCredentials)
    }

    @Test
    fun `another port on the engine's host carries no engine credentials`() = runTest {
        var credentials: Pair<String?, String?>? = null
        val engine = MockEngine { request ->
            credentials = request.credentials()
            respond("{}", HttpStatusCode.OK)
        }

        client(engine).get("https://agent.example.com:8443/doc")

        assertThat(credentials).isEqualTo(noCredentials)
    }

    @Test
    fun `the scheduler's client scopes the credentials to the scheduler, not the engine`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respond("{}", HttpStatusCode.OK)
        }
        val scheduler = client(engine, authority = { it.schedulerUrl })

        scheduler.get("https://sched.example.com/missions")
        scheduler.get("https://agent.example.com/doc")

        assertThat(seen[0].credentials().second).isEqualTo("Bearer bearer-1")
        assertThat(seen[1].credentials()).isEqualTo(noCredentials)
    }

    @Test
    fun `a client whose service is not configured sends no credential anywhere`() = runTest {
        // The scheduler is optional and its address blank by default; blank must match nothing,
        // or the engine's Basic would go to whatever host the request happened to name.
        var credentials: Pair<String?, String?>? = null
        val engine = MockEngine { request ->
            credentials = request.credentials()
            respond("{}", HttpStatusCode.OK)
        }

        client(engine, authority = { "" }).get("https://sched.example.com/missions")

        assertThat(credentials).isEqualTo(noCredentials)
    }

    @Test
    fun `a 401 from a foreign host does not spend a renewal`() = runTest {
        var renewals = 0
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }

        client(engine, renew = { renewals++; "bearer-2" }).get("https://cdn.example.net/asset")

        assertThat(renewals).isEqualTo(0)
    }
}
