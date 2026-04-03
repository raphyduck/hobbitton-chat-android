package com.garfiec.librechat.core.network.client

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AuthInterceptorTest {

    private class FakeTokenManager(
        private var accessToken: String? = "initial-token",
        private var refreshSucceeds: Boolean = true,
        private var refreshedToken: String = "refreshed-token",
    ) : TokenManager {
        var refreshCallCount = 0
        var sessionExpiredCount = 0
        private val _sessionExpiredFlow = MutableSharedFlow<Unit>()
        override val sessionExpiredFlow: SharedFlow<Unit> = _sessionExpiredFlow
        override val isAuthenticated: Boolean get() = accessToken != null

        override suspend fun getAccessToken(): String? = accessToken
        override suspend fun setTokens(accessToken: String, refreshToken: String) {
            this.accessToken = accessToken
        }

        override suspend fun refreshAccessToken(): Boolean {
            refreshCallCount++
            return if (refreshSucceeds) {
                accessToken = refreshedToken
                true
            } else {
                false
            }
        }

        override suspend fun clearTokens() {
            accessToken = null
        }

        override fun emitSessionExpired() {
            sessionExpiredCount++
        }
    }

    private fun createClient(
        tokenManager: FakeTokenManager,
        engine: MockEngine,
    ): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json() }
        install(AuthInterceptorPlugin) {
            this.tokenManager = tokenManager
        }
    }

    @Test
    fun `attaches Bearer token to requests`() = runTest {
        val tokenManager = FakeTokenManager(accessToken = "my-token")
        var capturedAuth: String? = null

        val engine = MockEngine { request ->
            capturedAuth = request.headers[HttpHeaders.Authorization]
            respond("OK", HttpStatusCode.OK)
        }
        val client = createClient(tokenManager, engine)

        client.get("https://example.com/api/conversations")
        assertThat(capturedAuth).isEqualTo("Bearer my-token")
    }

    @Test
    fun `skips auth for login endpoint`() = runTest {
        val tokenManager = FakeTokenManager(accessToken = "my-token")
        var capturedAuth: String? = null

        val engine = MockEngine { request ->
            capturedAuth = request.headers[HttpHeaders.Authorization]
            respond("OK", HttpStatusCode.OK)
        }
        val client = createClient(tokenManager, engine)

        client.get("https://example.com/auth/login")
        assertThat(capturedAuth).isNull()
    }

    @Test
    fun `skips auth for register endpoint`() = runTest {
        val tokenManager = FakeTokenManager(accessToken = "my-token")
        var capturedAuth: String? = null

        val engine = MockEngine { request ->
            capturedAuth = request.headers[HttpHeaders.Authorization]
            respond("OK", HttpStatusCode.OK)
        }
        val client = createClient(tokenManager, engine)

        client.get("https://example.com/auth/register")
        assertThat(capturedAuth).isNull()
    }

    @Test
    fun `refreshes token on 401 and retries`() = runTest {
        val tokenManager = FakeTokenManager(
            accessToken = "expired-token",
            refreshSucceeds = true,
            refreshedToken = "new-token",
        )
        var requestCount = 0
        val capturedTokens = mutableListOf<String?>()

        val engine = MockEngine { request ->
            requestCount++
            capturedTokens.add(request.headers[HttpHeaders.Authorization])
            if (requestCount == 1) {
                respond("Unauthorized", HttpStatusCode.Unauthorized)
            } else {
                respond("OK", HttpStatusCode.OK)
            }
        }
        val client = createClient(tokenManager, engine)

        val response = client.get("https://example.com/api/data")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(requestCount).isEqualTo(2)
        assertThat(tokenManager.refreshCallCount).isEqualTo(1)
        assertThat(capturedTokens[0]).isEqualTo("Bearer expired-token")
        assertThat(capturedTokens[1]).isEqualTo("Bearer new-token")
    }

    @Test
    fun `emits session expired when refresh fails`() = runTest {
        val tokenManager = FakeTokenManager(
            accessToken = "expired-token",
            refreshSucceeds = false,
        )

        val engine = MockEngine {
            respond("Unauthorized", HttpStatusCode.Unauthorized)
        }
        val client = createClient(tokenManager, engine)

        val response = client.get("https://example.com/api/data")
        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(tokenManager.refreshCallCount).isEqualTo(1)
        assertThat(tokenManager.sessionExpiredCount).isEqualTo(1)
    }

    @Test
    fun `returns 401 when refreshed token is also expired`() = runTest {
        val tokenManager = FakeTokenManager(
            accessToken = "expired-token",
            refreshSucceeds = true,
            refreshedToken = "still-expired",
        )

        val engine = MockEngine {
            respond("Unauthorized", HttpStatusCode.Unauthorized)
        }
        val client = createClient(tokenManager, engine)

        val response = client.get("https://example.com/api/data")
        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
        // Refresh was attempted once, then the retry 401 is returned as-is
        // (execute() inside interceptor doesn't re-enter the same interceptor)
        assertThat(tokenManager.refreshCallCount).isEqualTo(1)
    }

    @Test
    fun `does not refresh on non-401 errors`() = runTest {
        val tokenManager = FakeTokenManager()

        val engine = MockEngine {
            respond("Server Error", HttpStatusCode.InternalServerError)
        }
        val client = createClient(tokenManager, engine)

        val response = client.get("https://example.com/api/data")
        assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
        assertThat(tokenManager.refreshCallCount).isEqualTo(0)
    }

    @Test
    fun `sends request without token when no token available`() = runTest {
        val tokenManager = FakeTokenManager(accessToken = null)
        var capturedAuth: String? = "not-null"

        val engine = MockEngine { request ->
            capturedAuth = request.headers[HttpHeaders.Authorization]
            respond("OK", HttpStatusCode.OK)
        }
        val client = createClient(tokenManager, engine)

        client.get("https://example.com/api/data")
        assertThat(capturedAuth).isNull()
    }
}
