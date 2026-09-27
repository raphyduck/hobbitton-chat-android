package com.garfiec.librechat.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import com.garfiec.librechat.core.network.engine.EngineTokens
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * The engine's and the scheduler's clients as `engineModule` actually builds them (D-076): both
 * read the one portal session, and each presents its bearer to its own service only.
 *
 * `PortalServiceClientTest` (`:core:network`) pins the configuration function; this pins the
 * module's use of it — a client handed the other service's address would pass that test and fail
 * this one. The graph binds fakes for the platform pieces only: settings in an in-memory store,
 * tokens in a map, the network in a mock.
 */
class PortalServiceWiringTest {

    private var app: KoinApplication? = null
    private val seen = mutableListOf<HttpRequestData>()

    @After
    fun tearDown() {
        app?.close()
    }

    private class MemoryPreferences(initial: Preferences) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private object FreshTokens : EngineTokenStore {
        override suspend fun read(): EngineTokens = EngineTokens("portal-token", "refresh", expiresAtEpochSeconds = null)
        override suspend fun write(tokens: EngineTokens) = Unit
        override suspend fun clear() = Unit
    }

    private fun koin(): KoinApplication = app ?: koinApplication {
        allowOverride(true)
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
                                            content = "{}",
                                            status = HttpStatusCode.OK,
                                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                                        )
                                    }
                                },
                            )
                    }
                }
                single { Json { ignoreUnknownKeys = true } }
                single<CoroutineScope>(KoinQualifiers.ApplicationScope) { CoroutineScope(Dispatchers.Unconfined) }
                single<DataStore<Preferences>> {
                    MemoryPreferences(
                        mutablePreferencesOf(
                            stringPreferencesKey("engine_base_url") to "https://agent.example.com",
                            stringPreferencesKey("engine_issuer_url") to "https://auth.example.com",
                            stringPreferencesKey("scheduler_base_url") to "https://sched.example.com",
                        ),
                    )
                }
                // Overrides engineModule's, which needs an Android context and a keystore.
                single<EngineTokenStore> { FreshTokens }
            },
        )
    }.also { app = it }

    private fun client(qualifier: org.koin.core.qualifier.Qualifier): HttpClient = koin().koin.get(qualifier)

    private fun HttpRequestData.bearer(): String? = headers[HttpHeaders.ProxyAuthorization]

    @Test
    fun `the engine's client presents the portal bearer to the engine, and only there`() = runTest {
        val engine = client(KoinQualifiers.Engine)

        engine.get("https://agent.example.com/session")
        engine.get("https://sched.example.com/etat")

        assertThat(seen.map { it.bearer() }).containsExactly("Bearer portal-token", null).inOrder()
        assertThat(seen.map { it.headers[HttpHeaders.Authorization] }).containsExactly(null, null)
    }

    @Test
    fun `the scheduler's client presents it to the scheduler, and only there`() = runTest {
        val scheduler = client(KoinQualifiers.Scheduler)

        scheduler.get("https://sched.example.com/etat")
        scheduler.get("https://agent.example.com/session")

        assertThat(seen.map { it.bearer() }).containsExactly("Bearer portal-token", null).inOrder()
        assertThat(seen.map { it.headers[HttpHeaders.Authorization] }).containsExactly(null, null)
    }
}
