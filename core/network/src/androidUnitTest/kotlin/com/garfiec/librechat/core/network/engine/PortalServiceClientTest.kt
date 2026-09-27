package com.garfiec.librechat.core.network.engine

import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The engine's and the scheduler's clients, as the app builds them ([portalService]) — and where
 * each may send the one credential they share.
 *
 * The review before D-076 found the engine's Basic riding the scheduler's client, towards a host
 * that had no use for it. The Basic is gone, but the rule it broke stands for the bearer: each
 * client presents it to its own service and to nothing else — not to the other service, not to a
 * third host, not over a downgrade. These tests run on the configuration the Koin module installs,
 * not on a client assembled for the occasion, so a service wired to the wrong address fails here.
 */
class PortalServiceClientTest {

    private val access = EngineAccess(
        baseUrl = "https://agent.example.com",
        issuerUrl = "https://auth.example.com",
        schedulerUrl = "https://sched.example.com",
    )

    private val bearers = object : PortalBearerSource {
        override suspend fun bearer(): String = "portal-token"
        override suspend fun renew(): String? = null
    }

    private val seen = mutableListOf<HttpRequestData>()

    private fun mock(): MockEngine = MockEngine { request ->
        seen += request
        respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private fun client(service: PortalService, configured: EngineAccess? = access): HttpClient =
        HttpClient(mock()) {
            portalService(
                service = service,
                jsonFormat = librechatJson,
                accessOf = { configured },
                snapshot = { configured },
                bearers = bearers,
            )
        }

    private fun HttpRequestData.bearer(): String? = headers[HttpHeaders.ProxyAuthorization]

    @Test
    fun `each client addresses its own service by default`() = runTest {
        client(PortalService.ENGINE).get("session")
        client(PortalService.SCHEDULER).get("etat")

        assertThat(seen.map { it.url.host }).containsExactly("agent.example.com", "sched.example.com").inOrder()
        assertThat(seen.map { it.bearer() }).containsExactly("Bearer portal-token", "Bearer portal-token")
    }

    @Test
    fun `the engine's client never hands the bearer to the scheduler`() = runTest {
        client(PortalService.ENGINE).get("https://sched.example.com/etat")

        assertThat(seen.single().bearer()).isNull()
    }

    @Test
    fun `the scheduler's client never hands the bearer to the engine`() = runTest {
        client(PortalService.SCHEDULER).get("https://agent.example.com/session")

        assertThat(seen.single().bearer()).isNull()
    }

    @Test
    fun `neither client ever sends Authorization — the edge owns it`() = runTest {
        // D-076: the edge presents the engine's Basic. A value the app put here would be a service
        // secret on a phone, on every request, to both hosts — exactly what the review found.
        val engine = client(PortalService.ENGINE)
        val scheduler = client(PortalService.SCHEDULER)

        engine.get("session")
        engine.get("https://sched.example.com/etat")
        scheduler.get("etat")
        scheduler.get("https://agent.example.com/session")

        assertThat(seen).hasSize(4)
        assertThat(seen.map { it.headers[HttpHeaders.Authorization] }).containsExactly(null, null, null, null)
    }

    @Test
    fun `a redirect from the engine to the scheduler drops the bearer on the hop`() = runTest {
        // The same token opens both services, and still: a hop the engine's client did not choose
        // is not a place its credential goes. Ktor copies Proxy-Authorization across redirects.
        val hops = mutableListOf<HttpRequestData>()
        val redirecting = MockEngine { request ->
            hops += request
            if (request.url.host == "agent.example.com") {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://sched.example.com/etat"))
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }
        val engine = HttpClient(redirecting) {
            portalService(PortalService.ENGINE, librechatJson, { access }, { access }, bearers)
        }

        engine.get("session")

        assertThat(hops.map { it.url.host }).containsExactly("agent.example.com", "sched.example.com").inOrder()
        assertThat(hops[0].bearer()).isEqualTo("Bearer portal-token")
        assertThat(hops[1].bearer()).isNull()
    }

    @Test
    fun `a scheduler that is not configured gets no credential, wherever the request goes`() = runTest {
        val scheduler = client(PortalService.SCHEDULER, configured = access.copy(schedulerUrl = ""))

        scheduler.get("https://sched.example.com/etat")
        scheduler.get("https://agent.example.com/session")

        assertThat(seen.map { it.bearer() }).containsExactly(null, null)
    }
}
