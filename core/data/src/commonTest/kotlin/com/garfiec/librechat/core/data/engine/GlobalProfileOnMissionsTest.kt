package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.chat.GlobalProfile
import com.garfiec.librechat.core.network.api.AgentEngineApi
import com.garfiec.librechat.core.network.api.SchedulerApi
import com.garfiec.librechat.core.network.di.librechatJson
import com.garfiec.librechat.core.network.engine.EngineEventParser
import com.garfiec.librechat.core.network.engine.EngineEventTransport
import com.garfiec.librechat.core.network.engine.EngineStreamClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The global profile reaches the engine — on **every** turn, and by the same route the chat uses.
 *
 * This is the whole point of the convergence asked for on 02/09/2026: what is written once in the
 * settings governs a conversation and a mission alike. It is asserted at the wire, because that is
 * the only place the claim is true or false: a `system` the repository builds and forgets to send
 * looks identical from the inside, and shows up only as a model quietly ignoring its instructions.
 */
class GlobalProfileOnMissionsTest {

    private val sessionJson = """{"id":"ses_1","title":"Une mission"}"""
    private val messageJson = """{"info":{"id":"msg_1","role":"assistant"},"parts":[]}"""

    /** Every request the repository made, in order, so a test can read what actually left. */
    private val sent = mutableListOf<HttpRequestData>()

    private fun engine() = MockEngine { request ->
        sent += request
        val body = when {
            // `launch` reads the scheduler's connector catalogue to build the session's permission
            // rules, so a mock that serves a launch has to answer this too.
            request.url.encodedPath == "/mcp" -> connectorCatalogue()
            request.url.encodedPath.endsWith("/message") -> messageJson
            else -> sessionJson
        }
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private fun connectorCatalogue(): String {
        val payload = """{"connecteurs":{"memoire":{"outils":["memoire_lire"],""" +
            """"refuse_si_autonome":false}},"socle":{"todowrite":"allow"}}"""
        return """{"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":${
            JsonPrimitive(payload)
        }}]}}"""
    }

    /** The body the repository sent to a given engine route, as JSON. */
    private fun bodyOf(pathSuffix: String) =
        librechatJson.parseToJsonElement(
            (sent.last { it.url.encodedPath.endsWith(pathSuffix) }.body as TextContent).text,
        ).jsonObject

    private fun repository(profile: GlobalProfile, engine: MockEngine) = EngineMissionRepository(
        api = AgentEngineApi(
            HttpClient(engine) {
                install(ContentNegotiation) { json(librechatJson) }
                defaultRequest { contentType(ContentType.Application.Json) }
            },
        ),
        scheduler = SchedulerApi(
            HttpClient(engine) {
                install(ContentNegotiation) { json(librechatJson) }
                defaultRequest { contentType(ContentType.Application.Json) }
            },
            librechatJson,
        ),
        streamClient = EngineStreamClient(EngineEventParser(librechatJson)),
        eventTransport = object : EngineEventTransport {
            override fun stream(): Flow<ByteArray> = emptyFlow()
        },
        globalProfile = { profile },
    )

    /** The `system` of the last body sent, or null when the key was omitted entirely. */
    private fun lastSystem(): String? {
        val body = sent.last().body as TextContent
        return librechatJson.parseToJsonElement(body.text)
            .jsonObject["system"]
            ?.jsonPrimitive
            ?.content
    }

    @Test
    fun `a message in a mission carries the profile's instructions`() = runTest {
        val engine = engine()
        repository(GlobalProfile(instructions = "Réponds toujours en français."), engine)
            .sendMessage("ses_1", "bonjour")

        assertEquals("Réponds toujours en français.", lastSystem())
    }

    @Test
    fun `launching a mission carries them too`() = runTest {
        val engine = engine()
        repository(GlobalProfile(instructions = "Cite tes sources."), engine)
            .launch(objective = "faire le point", connectors = emptyList())

        // The instructions ride on the objective, not on the session: the creation route has no
        // place for them, and inventing one there is how a mission dies with a bare 400.
        assertNull(bodyOf("/session")["system"])
        assertEquals("Cite tes sources.", bodyOf("prompt_async")["system"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a parked profile sends nothing rather than an empty instruction`() = runTest {
        val engine = engine()
        repository(
            GlobalProfile(enabled = false, instructions = "Réponds toujours en français."),
            engine,
        ).sendMessage("ses_1", "bonjour")

        // Absent, not empty: an empty string is a system prompt that says nothing, which is a
        // different thing to hand a model than no system prompt at all.
        assertNull(lastSystem())
    }

    @Test
    fun `a blank profile sends nothing`() = runTest {
        val engine = engine()
        repository(GlobalProfile(instructions = "   "), engine).sendMessage("ses_1", "bonjour")

        assertNull(lastSystem())
    }

    @Test
    fun `the profile is re-read for each turn, so editing it changes the next message`() = runTest {
        val engine = engine()
        var instructions = "Sois bref."
        val repository = EngineMissionRepository(
            api = AgentEngineApi(
                HttpClient(engine) {
                    install(ContentNegotiation) { json(librechatJson) }
                    defaultRequest { contentType(ContentType.Application.Json) }
                },
            ),
            scheduler = SchedulerApi(
                HttpClient(engine) {
                    install(ContentNegotiation) { json(librechatJson) }
                    defaultRequest { contentType(ContentType.Application.Json) }
                },
                librechatJson,
            ),
            streamClient = EngineStreamClient(EngineEventParser(librechatJson)),
            eventTransport = object : EngineEventTransport {
                override fun stream(): Flow<ByteArray> = emptyFlow()
            },
            globalProfile = { GlobalProfile(instructions = instructions) },
        )

        repository.sendMessage("ses_1", "un")
        assertEquals("Sois bref.", lastSystem())

        instructions = "Sois détaillé."
        repository.sendMessage("ses_1", "deux")

        // A profile read once at construction would still be saying « Sois bref. » here — and the
        // settings screen would look like it had no effect until the app was restarted.
        assertEquals("Sois détaillé.", lastSystem())
    }

    @Test
    fun `the app names one agent and never asks the engine which profiles exist`() = runTest {
        val engine = engine()
        repository(GlobalProfile.NONE, engine).launch("faire le point", emptyList())

        assertEquals("mission", bodyOf("/session")["agent"]?.jsonPrimitive?.content)
        // `GET /agent` is gone: there is no profile to resolve any more, so nothing may go looking
        // for one — the round trip existed only to re-derive a constant.
        assertTrue(sent.none { it.url.encodedPath.endsWith("/agent") })
    }
}
