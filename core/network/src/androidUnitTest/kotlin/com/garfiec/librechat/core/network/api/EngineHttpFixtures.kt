package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.network.di.librechatJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

/**
 * What the engine and scheduler wire tests share: a client built like the real ones — the app's
 * `Json`, and `Content-Type: application/json` set in `defaultRequest`, which the API services rely
 * on rather than setting it per call — and the header a JSON answer carries.
 */
internal fun jsonHeaders(): Headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

internal fun engineTestClient(engine: MockEngine, baseUrl: String): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) { json(librechatJson) }
    defaultRequest {
        url(baseUrl)
        contentType(ContentType.Application.Json)
    }
}

/** The engine's API over [engine], as `AgentEngineApiTest` exercises it. */
internal fun agentEngineApi(engine: MockEngine): AgentEngineApi =
    AgentEngineApi(engineTestClient(engine, "https://agent.example.com"))

/** The scheduler's MCP client over [engine], as `SchedulerApiTest` exercises it. */
internal fun schedulerApi(engine: MockEngine): SchedulerApi =
    SchedulerApi(engineTestClient(engine, "https://sched.example.com"), librechatJson)
