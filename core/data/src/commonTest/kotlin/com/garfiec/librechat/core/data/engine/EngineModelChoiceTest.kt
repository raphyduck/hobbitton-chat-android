package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.network.api.AgentEngineApi
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
import io.ktor.client.utils.EmptyContent
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Choosing the model a mission runs on.
 *
 * The shape below is the live engine's, copied from `GET /config/providers` on 28/08/2026 — the
 * deployment's own gateway alongside the endpoint OpenCode ships with, which is precisely the pair
 * that makes the filtering necessary.
 */
class EngineModelChoiceTest {

    private val catalogue = """
        {
          "providers": [
            {
              "id": "opencode", "name": "OpenCode Zen", "source": "custom",
              "models": { "big-pickle": { "id": "big-pickle", "name": "Big Pickle" } }
            },
            {
              "id": "hobbitton-gateway", "name": "hobbitton (gateway LiteLLM)", "source": "config",
              "models": {
                "gpt-5.5": { "id": "gpt-5.5", "name": "GPT-5.5" },
                "claude-sonnet-5": { "id": "claude-sonnet-5", "name": "Claude Sonnet 5" }
              }
            }
          ],
          "default": { "opencode": "big-pickle", "hobbitton-gateway": "gpt-5.5" }
        }
    """.trimIndent()

    /**
     * Built like the real graph, `defaultRequest` included.
     *
     * Not decoration: the engine's client sets `Content-Type: application/json` there
     * (`NetworkModule`), and the API services rely on it — none of them calls `contentType`. A test
     * client without it fails at « Fail to prepare request body », which says nothing about the
     * code under test and everything about the harness.
     */
    private fun repository(engine: MockEngine) = EngineMissionRepository(
        api = AgentEngineApi(
            HttpClient(engine) {
                install(ContentNegotiation) { json(librechatJson) }
                defaultRequest { contentType(ContentType.Application.Json) }
            },
        ),
        // The chat's live feed is not exercised here — this suite is about the model catalogue — so
        // the stream side is stubbed: a real parser and an event transport that never emits.
        streamClient = EngineStreamClient(EngineEventParser(librechatJson)),
        eventTransport = object : EngineEventTransport {
            override fun stream(): Flow<ByteArray> = emptyFlow()
        },
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun catalogueEngine() = MockEngine { respond(catalogue, HttpStatusCode.OK, jsonHeaders()) }

    @Test
    fun `only the models of a provider this deployment declared are offered`() = runTest {
        val choice = repository(catalogueEngine()).models()

        // OpenCode ships its own endpoint, keyed and ready. A mission sent there leaves the
        // platform's gateway entirely — no cost accounting, no ceiling — so it must not be on
        // offer, however tempting a one-line filter is to delete later.
        assertTrue(choice.models.none { it.providerId == "opencode" })
        assertEquals(setOf("hobbitton-gateway"), choice.models.map { it.providerId }.toSet())
    }

    @Test
    fun `the list is ordered, because a map is not`() = runTest {
        val choice = repository(catalogueEngine()).models()

        // The engine hands back an object, whose key order nothing promises. A picker that
        // reshuffles between two openings is a picker that gets misread under a thumb.
        assertEquals(listOf("Claude Sonnet 5", "GPT-5.5"), choice.models.map { it.label })
    }

    @Test
    fun `the engine's own default is what gets preselected`() = runTest {
        val choice = repository(catalogueEngine()).models()

        // Not the first of the list: that would quietly make the alphabet decide which model this
        // deployment runs on.
        assertEquals("gpt-5.5", choice.preselected?.modelId)
        assertEquals("hobbitton-gateway", choice.preselected?.providerId)
    }

    @Test
    fun `an engine that names no default preselects nothing`() = runTest {
        val sansDefaut = catalogue.replace(
            """"default": { "opencode": "big-pickle", "hobbitton-gateway": "gpt-5.5" }""",
            """"default": {}""",
        )
        val choice = repository(
            MockEngine { respond(sansDefaut, HttpStatusCode.OK, jsonHeaders()) },
        ).models()

        assertTrue(choice.models.isNotEmpty())
        assertNull(choice.preselected)
    }

    @Test
    fun `the model travels with the prompt and never with the session`() = runTest {
        val bodies = mutableMapOf<String, String>()
        val engine = MockEngine { request ->
            bodies[request.url.encodedPath] = request.textBody()
            respond("""{"id":"ses_abc"}""", HttpStatusCode.OK, jsonHeaders())
        }

        repository(engine).launch(
            profile = "mission",
            objective = "fais le point",
            connectors = listOf("memoire"),
            model = EngineModelRef(providerId = "hobbitton-gateway", modelId = "claude-sonnet-5"),
        )

        // The one that matters. Since OpenCode 1.18.18, `POST /session` answers 400 to a `model`
        // key — naming no field, so it reads like a malformed request. The server lost three
        // nights to it on 24/08/2026: its only mission that named a model died in 0,0 s, before
        // the session existed. Nothing in the types prevents putting it back.
        assertFalse("model" in bodies.getValue("/session"))
        assertTrue("claude-sonnet-5" in bodies.getValue("/session/ses_abc/prompt_async"))
        assertTrue("hobbitton-gateway" in bodies.getValue("/session/ses_abc/prompt_async"))
    }

    @Test
    fun `no model chosen sends no model key at all`() = runTest {
        var promptBody = ""
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("prompt_async")) promptBody = request.textBody()
            respond("""{"id":"ses_abc"}""", HttpStatusCode.OK, jsonHeaders())
        }

        repository(engine).launch("mission", "fais le point", listOf("memoire"))

        // An absent key, not a null one: the engine's own default applies untouched, which is what
        // « I did not choose » has to mean.
        assertFalse("model" in promptBody)
    }
}

private fun HttpRequestData.textBody(): String = when (val content = body) {
    is TextContent -> content.text
    EmptyContent -> ""
    else -> content.toString()
}
