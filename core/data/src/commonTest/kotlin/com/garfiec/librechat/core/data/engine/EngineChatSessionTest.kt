package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.network.di.librechatJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A chat is an engine session on the chat profile (D-077), and the proof is on the wire: the agent,
 * the provider and the perimeter the repository actually sends.
 */
class EngineChatSessionTest {

    private val sent = mutableListOf<HttpRequestData>()

    /** Sessions the mock lists, and the transcript it serves for each. */
    private var sessionsJson = "[]"
    private var messagesBySession: Map<String, String> = emptyMap()

    private val providersJson = """
        {
          "providers": [
            {
              "id": "hobbitton-gateway", "name": "Gateway", "source": "config",
              "models": { "claude-sonnet-5": { "id": "claude-sonnet-5", "name": "Claude Sonnet 5" } }
            },
            {
              "id": "hobbitton-chat", "name": "Chat", "source": "config",
              "models": {
                "claude-sonnet-5": { "id": "claude-sonnet-5", "name": "Claude Sonnet 5" },
                "gpt-5.5": { "id": "gpt-5.5", "name": "GPT-5.5" }
              }
            }
          ],
          "default": { "hobbitton-gateway": "claude-sonnet-5", "hobbitton-chat": "claude-sonnet-5" }
        }
    """.trimIndent()

    private fun engine() = MockEngine { request ->
        sent += request
        val path = request.url.encodedPath
        val body = when {
            path == "/mcp" -> {
                val text = (request.body as TextContent).text
                if ("\"perimetre\"" in text) toolFrame("""{"session":"ses_chat","connecteurs":[],"enregistre":true}""") else catalogueFrame()
            }
            path == "/config/providers" -> providersJson
            path == "/session/status" -> "{}"
            path == "/session" && request.method == HttpMethod.Get -> sessionsJson
            path == "/session" -> """{"id":"ses_chat","title":"Bonjour"}"""
            path.endsWith("/message") && request.method == HttpMethod.Get ->
                messagesBySession[path.removePrefix("/session/").removeSuffix("/message")] ?: "[]"
            path.endsWith("/message") -> """{"info":{"id":"msg_1","role":"assistant"},"parts":[]}"""
            else -> "{}"
        }
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private fun catalogueFrame(): String = toolFrame(
        """{"connecteurs":{""" +
            """"memoire":{"outils":["memoire_lire"],"chat":true},""" +
            """"planificateur":{"outils":["planificateur_lister"],"chat":true},""" +
            """"banque":{"outils":["banque_soldes"],"direct":false,"chat":true},""" +
            """"shell":{"outils":["bash"],"chat":false},""" +
            """"web":{"outils":["webfetch"]}""" +
            """},"socle":{"todowrite":"allow"}}""",
    )

    private fun toolFrame(payload: String): String =
        """{"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":${JsonPrimitive(payload)}}]}}"""

    private fun bodyOf(predicate: (HttpRequestData) -> Boolean): JsonObject =
        librechatJson.parseToJsonElement((sent.last(predicate).body as TextContent).text).jsonObject

    private fun isPost(path: String): (HttpRequestData) -> Boolean =
        { it.method == HttpMethod.Post && it.url.encodedPath == path }

    @Test
    fun `a new chat is a session on the chat agent`() = runTest {
        testMissionRepository(engine()).startChat("Bonjour")

        assertEquals("chat", bodyOf(isPost("/session"))["agent"]?.jsonPrimitive?.content)
        assertEquals("chat", bodyOf(isPost("/session/ses_chat/prompt_async"))["agent"]?.jsonPrimitive?.content)
    }

    @Test
    fun `its perimeter is every connector open to a chat, recorded with the scheduler`() = runTest {
        testMissionRepository(engine()).startChat("Bonjour")

        val scope = sent.last { "\"perimetre\"" in ((it.body as? TextContent)?.text ?: "") }
        val arguments = librechatJson.parseToJsonElement((scope.body as TextContent).text)
            .jsonObject["params"]!!.jsonObject["arguments"]!!.jsonObject
        assertEquals<List<String>>(
            listOf("banque", "memoire", "planificateur"),
            arguments["connecteurs"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        // …and the scope is recorded BEFORE the first prompt, as a mission's is (D-071).
        val scopeIndex = sent.indexOf(scope)
        val promptIndex = sent.indexOfFirst { it.url.encodedPath.endsWith("/prompt_async") }
        assertTrue(scopeIndex < promptIndex)
    }

    @Test
    fun `its rules open the direct chat connectors and nothing closed to chats`() = runTest {
        testMissionRepository(engine()).startChat("Bonjour")

        val allowed = bodyOf(isPost("/session"))["permission"]!!.jsonArray
            .map { it.jsonObject }
            .filter { it["action"]?.jsonPrimitive?.content == "allow" }
            .map { it["permission"]!!.jsonPrimitive.content }
        assertTrue("memoire_lire" in allowed)
        assertTrue("planificateur_lister" in allowed)
        assertTrue("bash" !in allowed)
        assertTrue("webfetch" !in allowed)
        // The annuaire serves `banque`: in the scope, never in the model's tool list (D-071).
        assertTrue("banque_soldes" !in allowed)
    }

    @Test
    fun `its model comes from the chat provider, the provider's default when none is named`() = runTest {
        testMissionRepository(engine()).startChat("Bonjour")

        val model = bodyOf(isPost("/session/ses_chat/prompt_async"))["model"]!!.jsonObject
        assertEquals("hobbitton-chat", model["providerID"]?.jsonPrimitive?.content)
        assertEquals("claude-sonnet-5", model["modelID"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a model picked on the gateway is moved onto the chat provider`() = runTest {
        testMissionRepository(engine())
            .startChat("Bonjour", model = EngineModelRef(providerId = "hobbitton-gateway", modelId = "gpt-5.5"))

        val model = bodyOf(isPost("/session/ses_chat/prompt_async"))["model"]!!.jsonObject
        assertEquals("hobbitton-chat", model["providerID"]?.jsonPrimitive?.content)
        assertEquals("gpt-5.5", model["modelID"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the new session is recorded as a chat`() = runTest {
        val kinds = InMemorySessionKinds()
        testMissionRepository(engine(), kinds = kinds).startChat("Bonjour")

        assertEquals(EngineSessionKind.CHAT, kinds.recorded["ses_chat"])
    }

    @Test
    fun `every chat turn names the chat agent and provider`() = runTest {
        testMissionRepository(engine()).sendMessage(
            sessionId = "ses_chat",
            text = "et ensuite ?",
            model = EngineModelRef(providerId = "hobbitton-gateway", modelId = "claude-sonnet-5"),
            profile = EngineProfile.CHAT,
        )

        val body = bodyOf(isPost("/session/ses_chat/message"))
        assertEquals("chat", body["agent"]?.jsonPrimitive?.content)
        assertEquals("hobbitton-chat", body["model"]!!.jsonObject["providerID"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a task turn is left as it was`() = runTest {
        testMissionRepository(engine()).sendMessage(sessionId = "ses_task", text = "et ensuite ?")

        val body = bodyOf(isPost("/session/ses_task/message"))
        assertNull(body["agent"])
        assertNull(body["model"])
    }

    @Test
    fun `the chat picker offers the chat provider, the tasks picker everything else`() = runTest {
        val repository = testMissionRepository(engine())

        assertEquals(setOf("hobbitton-chat"), repository.models(EngineProfile.CHAT).models.map { it.providerId }.toSet())
        assertEquals(setOf("hobbitton-gateway"), repository.models(EngineProfile.TASK).models.map { it.providerId }.toSet())
        assertEquals("hobbitton-chat", repository.models(EngineProfile.CHAT).preselected?.providerId)
    }

    @Test
    fun `the drawer lists chats, and the Tasks tab does not`() = runTest {
        sessionsJson = """[
            {"id":"ses_here","title":"Une conversation d'ici","time":{"updated":400}},
            {"id":"ses_other","title":"Une conversation d'ailleurs","time":{"updated":300}},
            {"id":"ses_run","title":"veille — 2026-09-23T06:30+0200","time":{"updated":200}},
            {"id":"ses_mission","title":"Ranger les factures","time":{"updated":100}}
        ]"""
        messagesBySession = mapOf(
            "ses_other" to """[{"info":{"id":"m1","role":"user","agent":"chat"},"parts":[]}]""",
            "ses_mission" to """[{"info":{"id":"m2","role":"user","agent":"mission"},"parts":[]}]""",
            "ses_run" to """[{"info":{"id":"m3","role":"user","agent":"veille"},"parts":[]}]""",
        )
        val kinds = InMemorySessionKinds(mapOf("ses_here" to EngineSessionKind.CHAT))
        val repository = testMissionRepository(engine(), kinds = kinds)

        val chats = repository.recentChats()
        assertEquals<List<String>>(listOf("ses_here", "ses_other"), chats.map { it.sessionId })
        // What was learned from a transcript is kept, so it is never read again.
        assertEquals(EngineSessionKind.CHAT, kinds.recorded["ses_other"])
        assertEquals(EngineSessionKind.TASK, kinds.recorded["ses_mission"])

        val missions = repository.recentMissions()
        assertEquals<List<String>>(listOf("ses_run", "ses_mission"), missions.map { it.sessionId })
    }

    @Test
    fun `a session the scheduler titled is never read to be classified`() = runTest {
        sessionsJson = """[{"id":"ses_run","title":"veille — manuel","time":{"updated":1}}]"""

        testMissionRepository(engine()).recentChats()

        assertTrue(sent.none { it.url.encodedPath == "/session/ses_run/message" })
    }
}
