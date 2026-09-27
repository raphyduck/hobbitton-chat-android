package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.data.datastore.GlobalProfileSource
import com.garfiec.librechat.core.model.chat.GlobalProfile
import com.garfiec.librechat.core.network.api.AgentEngineApi
import com.garfiec.librechat.core.network.api.SchedulerApi
import com.garfiec.librechat.core.network.di.librechatJson
import com.garfiec.librechat.core.network.engine.EngineEventParser
import com.garfiec.librechat.core.network.engine.EngineEventTransport
import com.garfiec.librechat.core.network.engine.EngineStreamClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A mission repository over one mock engine — the engine and the scheduler both answer from it,
 * the way the suites that exercise `launch` need (the scheduler's catalogue and scope travel on
 * the same `/mcp` route of the same mock).
 *
 * The live feed is stubbed: a real parser, and a transport that never emits. None of the suites
 * built on this reads the feed.
 */
internal fun testMissionRepository(
    engine: MockEngine,
    globalProfile: GlobalProfileSource = GlobalProfileSource { GlobalProfile.NONE },
): EngineMissionRepository = EngineMissionRepository(
    api = AgentEngineApi(engineTestClient(engine)),
    scheduler = SchedulerApi(engineTestClient(engine), librechatJson),
    streamClient = EngineStreamClient(EngineEventParser(librechatJson)),
    eventTransport = SilentEventTransport,
    globalProfile = globalProfile,
)

/**
 * Built like the real graph, `defaultRequest` included.
 *
 * Not decoration: the engine's client sets `Content-Type: application/json` there
 * (`engineModule`, `EngineModule.android.kt`), and the API services rely on it — none of them
 * calls `contentType`. A test client without it fails at « Fail to prepare request body », which
 * says nothing about the code under test and everything about the harness.
 */
internal fun engineTestClient(engine: MockEngine): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) { json(librechatJson) }
    defaultRequest { contentType(ContentType.Application.Json) }
}

private object SilentEventTransport : EngineEventTransport {
    override fun stream(): Flow<ByteArray> = emptyFlow()
}
