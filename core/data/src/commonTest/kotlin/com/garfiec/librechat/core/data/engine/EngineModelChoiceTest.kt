package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EngineModelRef
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.utils.EmptyContent
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Choosing the model a conversation runs on.
 *
 * The shape below is the live engine's, copied from `GET /config/providers` on 28/08/2026 — the
 * deployment's own providers alongside the endpoint OpenCode ships with, which is precisely what
 * makes the filtering necessary. Since D-082 only `hobbitton-chat` is offered: the gateway's own
 * provider lists the same models on the scheduled missions' budget.
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
            },
            {
              "id": "hobbitton-chat", "name": "hobbitton (chat)", "source": "config",
              "models": {
                "gpt-5.5": { "id": "gpt-5.5", "name": "GPT-5.5" },
                "claude-sonnet-5": { "id": "claude-sonnet-5", "name": "Claude Sonnet 5",
                  "modalities": { "input": ["text", "image", "pdf"], "output": ["text"] } },
                "text-only": { "id": "text-only", "name": "Text only",
                  "modalities": { "input": ["text"], "output": ["text"] } }
              }
            }
          ],
          "default": { "opencode": "big-pickle", "hobbitton-gateway": "claude-sonnet-5", "hobbitton-chat": "gpt-5.5" }
        }
    """.trimIndent()

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun catalogueEngine() = MockEngine { respond(catalogue, HttpStatusCode.OK, jsonHeaders()) }

    @Test
    fun `only the conversations' provider, as this deployment declared it, is offered`() = runTest {
        val choice = testMissionRepository(catalogueEngine()).models()

        // OpenCode ships its own endpoint, keyed and ready. A turn sent there leaves the
        // platform's gateway entirely — no cost accounting, no ceiling — so it must not be on
        // offer, however tempting a one-line filter is to delete later. The gateway's provider
        // spends the scheduled missions' budget (D-082).
        assertTrue(choice.models.none { it.providerId == "opencode" })
        assertEquals(setOf("hobbitton-chat"), choice.models.map { it.providerId }.toSet())
    }

    @Test
    fun `the list is ordered, because a map is not`() = runTest {
        val choice = testMissionRepository(catalogueEngine()).models()

        // The engine hands back an object, whose key order nothing promises. A picker that
        // reshuffles between two openings is a picker that gets misread under a thumb.
        assertEquals(listOf("Claude Sonnet 5", "GPT-5.5", "Text only"), choice.models.map { it.label })
    }

    @Test
    fun `a model sees images unless its declared modalities leave them out`() = runTest {
        val models = testMissionRepository(catalogueEngine()).models().models.associateBy { it.modelId }

        // Declared with « image »: sees. Declared without: blind, and the composer warns before a
        // photo leaves for it (24/10/2026). Nothing declared: unknown, taken as seeing.
        assertTrue(models.getValue("claude-sonnet-5").seesImages)
        assertFalse(models.getValue("text-only").seesImages)
        assertTrue(models.getValue("gpt-5.5").seesImages)
    }

    @Test
    fun `the engine's own default is what gets preselected`() = runTest {
        val choice = testMissionRepository(catalogueEngine()).models()

        // Not the first of the list: that would quietly make the alphabet decide which model this
        // deployment runs on.
        assertEquals("gpt-5.5", choice.preselected?.modelId)
        assertEquals("hobbitton-chat", choice.preselected?.providerId)
    }

    @Test
    fun `the default set in Settings is preselected, the engine's kept beside it`() = runTest {
        val choice = testMissionRepository(
            catalogueEngine(),
            modelPreference = ModelPreference { "claude-sonnet-5" },
        ).models()

        assertEquals("claude-sonnet-5", choice.preselected?.modelId)
        assertEquals("hobbitton-chat", choice.preselected?.providerId)
        // Settings names it on « the server's » choice.
        assertEquals("gpt-5.5", choice.engineDefault?.modelId)
    }

    @Test
    fun `a default the deployment no longer lists falls back to the engine's`() = runTest {
        val choice = testMissionRepository(
            catalogueEngine(),
            modelPreference = ModelPreference { "a-model-the-gateway-dropped" },
        ).models()

        // A turn sent to a model the gateway no longer carries would only fail.
        assertEquals("gpt-5.5", choice.preselected?.modelId)
    }

    @Test
    fun `an engine that names no default preselects nothing`() = runTest {
        val sansDefaut = catalogue.replace(
            """"default": { "opencode": "big-pickle", "hobbitton-gateway": "claude-sonnet-5", "hobbitton-chat": "gpt-5.5" }""",
            """"default": {}""",
        )
        val choice = testMissionRepository(
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
            if (request.url.encodedPath == "/mcp") {
                respond(schedulerFrame(request.textBody()), HttpStatusCode.OK, jsonHeaders())
            } else {
                respond("""{"id":"ses_abc"}""", HttpStatusCode.OK, jsonHeaders())
            }
        }

        testMissionRepository(engine).startChat(
            text = "fais le point",
            connectors = listOf("memoire"),
            model = EngineModelRef(providerId = "hobbitton-gateway", modelId = "claude-sonnet-5"),
        )

        // The one that matters. Since OpenCode 1.18.18, `POST /session` answers 400 to a `model`
        // key — naming no field, so it reads like a malformed request. The server lost three
        // nights to it on 24/08/2026: its only mission that named a model died in 0,0 s, before
        // the session existed. Nothing in the types prevents putting it back.
        assertFalse("model" in bodies.getValue("/session"))
        assertTrue("claude-sonnet-5" in bodies.getValue("/session/ses_abc/prompt_async"))
        // …on the conversations' provider, whatever provider the pick named (D-082).
        assertTrue("hobbitton-chat" in bodies.getValue("/session/ses_abc/prompt_async"))
    }

    @Test
    fun `no model chosen and no default named sends no model key at all`() = runTest {
        var promptBody = ""
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("prompt_async")) promptBody = request.textBody()
            if (request.url.encodedPath == "/mcp") {
                respond(schedulerFrame(request.textBody()), HttpStatusCode.OK, jsonHeaders())
            } else {
                respond("""{"id":"ses_abc"}""", HttpStatusCode.OK, jsonHeaders())
            }
        }

        testMissionRepository(engine).startChat("fais le point", listOf("memoire"))

        // The engine named no default for the conversations' provider here: an absent key, not a
        // null one, so the engine's own default applies untouched.
        assertFalse("model" in promptBody)
    }
}

/**
 * The scheduler's answer to whichever of its tools the request body names: the connector catalogue,
 * or the record of a session's scope (`perimetre`, D-071), which `startChat` writes right after the
 * session is created. Both travel on the one `/mcp` route, so a mock has to read the body.
 */
private fun schedulerFrame(body: String): String =
    if ("\"perimetre\"" in body) scopeFrame() else connectorCatalogueFrame()

private fun scopeFrame(): String {
    val payload = """{"session":"ses_abc","connecteurs":["memoire"],"enregistre":true}"""
    return """{"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":${
        kotlinx.serialization.json.JsonPrimitive(payload)
    }}]}}"""
}

/**
 * What the scheduler answers when asked for the connector catalogue, wrapped as MCP returns it.
 *
 * `launch` reads the catalogue to build the session's permission rules — it no longer holds a table
 * of its own — so any mock engine that serves a launch has to answer this too.
 */
private fun connectorCatalogueFrame(): String {
    val payload = """{"connecteurs":{"memoire":{"outils":["memoire_lire"],""" +
        """"refuse_si_autonome":false}},"socle":{"todowrite":"allow"}}"""
    return """{"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":${
        kotlinx.serialization.json.JsonPrimitive(payload)
    }}]}}"""
}

private fun HttpRequestData.textBody(): String = when (val content = body) {
    is TextContent -> content.text
    EmptyContent -> ""
    else -> content.toString()
}
