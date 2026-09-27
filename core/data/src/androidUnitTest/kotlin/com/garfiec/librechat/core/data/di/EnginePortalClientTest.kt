package com.garfiec.librechat.core.data.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.network.client.AuthInterceptorPlugin
import com.garfiec.librechat.core.network.client.CleartextGuardPlugin
import com.garfiec.librechat.core.network.client.ServerHeadersPlugin
import com.garfiec.librechat.core.network.client.SwitchBarrierPlugin
import com.garfiec.librechat.core.network.engine.EngineAuthPlugin
import com.garfiec.librechat.core.network.engine.auth.EngineTokenClient
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * The portal's client is bare (finding M1, 26/09/2026), asserted on the graph the app builds.
 *
 * Two things a test of `EngineTokenClient` alone cannot see: which client the module hands it, and
 * what that client carries. The graph here binds none of LibreChat's identity — no token manager,
 * no server URL, no gateway headers — so if the token client ever went back to the chat's client,
 * resolving it would fail right here rather than leak on a shared-host deployment.
 */
class EnginePortalClientTest {

    private var app: KoinApplication? = null
    private val seen = mutableListOf<HttpRequestData>()

    @After
    fun tearDown() {
        app?.close()
    }

    private fun koin(): KoinApplication = app ?: koinApplication {
        modules(
            engineModule,
            module {
                single<HttpClientEngineFactory<*>> {
                    object : HttpClientEngineFactory<MockEngineConfig> {
                        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
                            MockEngine(
                                MockEngineConfig().apply(block).apply {
                                    addHandler { request ->
                                        seen += request
                                        respond(
                                            content = DISCOVERY,
                                            status = HttpStatusCode.OK,
                                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                                        )
                                    }
                                },
                            )
                    }
                }
                single { Json { ignoreUnknownKeys = true } }
            },
        )
    }.also { app = it }

    private fun portalClient(): HttpClient = koin().koin.get(KoinQualifiers.Portal)

    @Test
    fun `the portal client carries none of LibreChat's identity plugins`() {
        val client = portalClient()

        assertThat(client.pluginOrNull(AuthInterceptorPlugin)).isNull()
        assertThat(client.pluginOrNull(ServerHeadersPlugin)).isNull()
        assertThat(client.pluginOrNull(SwitchBarrierPlugin)).isNull()
        // Nor the engine's own: the Basic has no business at the token endpoint either.
        assertThat(client.pluginOrNull(EngineAuthPlugin)).isNull()
        // Nor a retry ladder: a token endpoint answers once, and a replayed exchange spends the code.
        assertThat(client.pluginOrNull(HttpRequestRetry)).isNull()
    }

    @Test
    fun `the portal client refuses cleartext to a public host like every other`() {
        assertThat(portalClient().pluginOrNull(CleartextGuardPlugin)).isNotNull()
    }

    @Test
    fun `the token client is wired to the portal client and sends a bare request`() = runTest {
        val tokens: EngineTokenClient = koin().koin.get()

        tokens.discover("https://auth.example.com")

        val request = seen.single()
        assertThat(request.url.toString()).isEqualTo("https://auth.example.com/.well-known/openid-configuration")
        assertThat(request.headers[HttpHeaders.Authorization]).isNull()
        assertThat(request.headers[HttpHeaders.ProxyAuthorization]).isNull()
        assertThat(request.headers[HttpHeaders.Cookie]).isNull()
    }

    private companion object {
        const val DISCOVERY = """{"issuer":"https://auth.example.com",""" +
            """"token_endpoint":"https://auth.example.com/api/oidc/token",""" +
            """"authorization_endpoint":"https://auth.example.com/api/oidc/authorization"}"""
    }
}
