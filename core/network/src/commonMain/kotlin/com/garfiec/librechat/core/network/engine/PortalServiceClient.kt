package com.garfiec.librechat.core.network.engine

import com.garfiec.librechat.core.network.client.CleartextGuardPlugin
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The services the portal's bearer opens, each with the one address its client may send it to.
 *
 * The same token opens both — its audience names the engine and the scheduler — but each client
 * presents it to its own service only. A request from the engine's client to the scheduler's host
 * (or the reverse) carries nothing: two clients, two authorities, never crossed.
 */
enum class PortalService(
    /** The address this service's client speaks to, and the only authority its bearer may reach. */
    val address: (EngineAccess) -> String,
) {
    ENGINE({ it.baseUrl }),
    SCHEDULER({ it.schedulerUrl }),
}

/**
 * Configures the client of one [service] behind the portal.
 *
 * The engine's and the scheduler's clients were two copies of the same fourteen lines, differing
 * in the address — and in one line that was easy to forget when copying: the authority the bearer
 * is scoped to. Built from one definition, they cannot drift apart, and the host scoping is tested
 * on what the app actually installs (`PortalServiceClientTest`).
 *
 * - [EngineAuthPlugin] with the [bearers] of the one portal session, scoped to [service]'s address;
 * - [CleartextGuardPlugin]: the bearer renews itself offline, so in the clear to a public host once
 *   is too many (M4);
 * - short timeouts: a mission is not a request — the engine answers `prompt_async` at once, the
 *   scheduler's `lancer` answers as soon as the session exists (server-side D-041) — and the live
 *   feed has its own transport;
 * - a default base URL read from [snapshot], because `defaultRequest` is not a coroutine. A blank
 *   address sets none, and the request then names its own host, which the plugin refuses to
 *   credential.
 */
fun HttpClientConfig<*>.portalService(
    service: PortalService,
    jsonFormat: Json,
    accessOf: suspend () -> EngineAccess?,
    snapshot: () -> EngineAccess?,
    bearers: PortalBearerSource,
) {
    install(ContentNegotiation) { json(jsonFormat) }
    install(EngineAuthPlugin) {
        access = accessOf
        bearerFrom(bearers)
        authority = service.address
    }
    install(CleartextGuardPlugin)
    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
    }
    defaultRequest {
        snapshot()?.let(service.address)
            ?.takeIf { it.isNotBlank() }
            ?.let { url.takeFrom(it) }
        contentType(ContentType.Application.Json)
    }
}

private const val CONNECT_TIMEOUT_MS = 10_000L
private const val REQUEST_TIMEOUT_MS = 30_000L
