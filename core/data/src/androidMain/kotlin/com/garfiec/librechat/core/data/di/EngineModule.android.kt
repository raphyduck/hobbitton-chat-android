package com.garfiec.librechat.core.data.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.data.engine.EngineSecureStore
import com.garfiec.librechat.core.data.engine.EngineSessionManager
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.network.api.AgentEngineApi
import com.garfiec.librechat.core.network.engine.EngineAuthPlugin
import com.garfiec.librechat.core.network.engine.EnginePasswordStore
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import com.garfiec.librechat.core.network.engine.auth.EngineOAuthEndpoints
import com.garfiec.librechat.core.network.engine.auth.EngineTokenClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The Agent engine's own graph: its HTTP client, its OAuth client, its stores.
 *
 * **Android only, for now, and deliberately.** The engine's secrets need a secure store, and on iOS
 * that means raw Keychain code (`SecItemAdd` and friends) that cannot be compiled or run from the
 * environment this was written in — only CI's macOS runner can. Shipping untested Keychain code to
 * find out is exactly the kind of guess that has cost this project its worst afternoons. The brief's
 * exit criterion for this phase is « Android, two tabs » (§9), so Android is where this lands.
 *
 * Reversing that is a known, bounded piece of work: an iOS `EngineSecureStore` over Keychain, an
 * `expect`/`actual` for it, and the Tasks module moved from `librechat.mobile.feature` to
 * `librechat.kmp.feature`. Recorded as D-034 in the server-side decision log.
 */
val engineModule: Module = module {

    single { EngineSecureStore(androidContext(), get(KoinQualifiers.IO)) }
    single<EngineTokenStore> { get<EngineSecureStore>().tokens }
    single<EnginePasswordStore> { get<EngineSecureStore>().password }

    single {
        EngineSettingsStore(dataStore = get(), passwords = get()).also { store ->
            // Warm the snapshot off the startup thread: the first engine request must not find an
            // empty base URL and fail as « unknown host » on a phone whose network is fine.
            get<CoroutineScope>(KoinQualifiers.ApplicationScope).launch { store.access() }
        }
    }

    /**
     * A **second** client, not the chat's.
     *
     * The chat's client is wired to LibreChat's base URL, its bearer, its refresh loop and its
     * account-switch barrier. Pointing it at the engine would send the chat's session cookie to a
     * host that has no idea what to do with it, and none of the two credentials the engine wants.
     */
    single(KoinQualifiers.Engine) {
        val settings = get<EngineSettingsStore>()
        val sessions = get<EngineSessionManager>()
        HttpClient(get<HttpClientEngineFactory<*>>()) {
            install(ContentNegotiation) { json(get<Json>()) }
            install(EngineAuthPlugin) {
                access = { settings.access() }
                bearer = { sessions.bearer() }
                renew = { sessions.renew() }
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                // A mission is not a request: the engine answers `prompt_async` at once, and every
                // other call here is small. Long-poll behaviour belongs to the event stream, which
                // has its own transport.
                requestTimeoutMillis = 30_000
            }
            defaultRequest {
                // Not a coroutine, hence the snapshot — warmed below and refreshed on every
                // suspend read. Same constraint as ServerUrlProvider's plain getter.
                settings.cachedAccess()?.let { url.takeFrom(it.baseUrl) }
                contentType(ContentType.Application.Json)
            }
        }
    }

    single { AgentEngineApi(get(KoinQualifiers.Engine)) }

    /**
     * The OAuth client talks to the **portal**, not the engine, and carries none of the engine's
     * credentials: mixing them would put the engine's Basic on every token request.
     */
    single { EngineTokenClient(client = get(), clientId = "hobbitton-chat-android") }

    single {
        EngineSessionManager(
            store = get(),
            client = get(),
            endpoints = { get<EngineSettingsStore>().access()?.let { discoveredEndpoints(it.issuerUrl, get()) } },
            now = { kotlin.time.Clock.System.now().epochSeconds },
        )
    }
}

/**
 * Discovery, cached for the lifetime of the process.
 *
 * Read from the issuer rather than assumed: the paths are Authelia's to change, and a hardcoded one
 * fails at the worst moment with a 404 that names nothing useful.
 */
private val discovered = mutableMapOf<String, EngineOAuthEndpoints>()

private suspend fun discoveredEndpoints(
    issuerUrl: String,
    client: EngineTokenClient,
): EngineOAuthEndpoints? {
    if (issuerUrl.isBlank()) return null
    discovered[issuerUrl]?.let { return it }
    return runCatching { client.discover(issuerUrl) }.getOrNull()?.also { discovered[issuerUrl] = it }
}
