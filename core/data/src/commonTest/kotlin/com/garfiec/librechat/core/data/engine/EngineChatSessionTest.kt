package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EnginePromptPart
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
import kotlin.test.assertTrue

/**
 * Every conversation is an engine session on the `chat` profile (D-077, D-082), and the proof is on
 * the wire: the agent, the provider and the perimeter the repository actually sends.
 */
class EngineChatSessionTest {

    private val sent = mutableListOf<HttpRequestData>()

    /** Sessions the mock lists, and the transcript it serves for each. */
    private var sessionsJson = "[]"
    private var messagesBySession: Map<String, String> = emptyMap()

    /** What the scheduler's `historique(json_brut=True)` answers. */
    private var runsJson = "[]"
    private var serverReadsJson = "{}"

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
                when {
                    "\"perimetre\"" in text -> toolFrame("""{"session":"ses_chat","connecteurs":[],"enregistre":true}""")
                    "\"historique\"" in text -> toolFrame(runsJson)
                    "\"conversations_lues\"" in text -> toolFrame(serverReadsJson)
                    else -> catalogueFrame()
                }
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
    fun `a new conversation is a session on the chat agent`() = runTest {
        testMissionRepository(engine()).startChat("Bonjour", connectors = listOf("memoire"))

        assertEquals("chat", bodyOf(isPost("/session"))["agent"]?.jsonPrimitive?.content)
        assertEquals("chat", bodyOf(isPost("/session/ses_chat/prompt_async"))["agent"]?.jsonPrimitive?.content)
    }

    @Test
    fun `its perimeter is the connectors ticked on the composer, recorded with the scheduler`() = runTest {
        testMissionRepository(engine()).startChat("Bonjour", connectors = listOf("planificateur", "banque", "memoire"))

        val scope = sent.last { "\"perimetre\"" in ((it.body as? TextContent)?.text ?: "") }
        val arguments = librechatJson.parseToJsonElement((scope.body as TextContent).text)
            .jsonObject["params"]!!.jsonObject["arguments"]!!.jsonObject
        assertEquals<List<String>>(
            listOf("banque", "memoire", "planificateur"),
            arguments["connecteurs"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        // …and the scope is recorded BEFORE the first prompt (D-071).
        val scopeIndex = sent.indexOf(scope)
        val promptIndex = sent.indexOfFirst { it.url.encodedPath.endsWith("/prompt_async") }
        assertTrue(scopeIndex < promptIndex)
    }

    @Test
    fun `its rules open the ticked direct connectors, and the shell only when ticked`() = runTest {
        testMissionRepository(engine()).startChat("Bonjour", connectors = listOf("memoire", "banque"))

        val allowed = allowedOn(bodyOf(isPost("/session")))
        assertTrue("memoire_lire" in allowed)
        assertTrue("planificateur_lister" !in allowed)
        assertTrue("bash" !in allowed)
        // The annuaire serves `banque`: in the scope, never in the model's tool list (D-071).
        assertTrue("banque_soldes" !in allowed)

        sent.clear()
        testMissionRepository(engine()).startChat("Bonjour", connectors = listOf("memoire", "shell"))
        assertTrue("bash" in allowedOn(bodyOf(isPost("/session"))))
    }

    @Test
    fun `unknown ticks start on the scheduler's defaults, not on nothing`() = runTest {
        testMissionRepository(engine()).startChat("Bonjour", connectors = null)

        val scope = sent.last { "\"perimetre\"" in ((it.body as? TextContent)?.text ?: "") }
        val arguments = librechatJson.parseToJsonElement((scope.body as TextContent).text)
            .jsonObject["params"]!!.jsonObject["arguments"]!!.jsonObject
        // `banque` is served by the annuaire, so ticked (D-071); no direct one is `defaut` here.
        assertEquals<List<String>>(
            listOf("banque"),
            arguments["connecteurs"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    private fun allowedOn(session: JsonObject): List<String> = session["permission"]!!.jsonArray
        .map { it.jsonObject }
        .filter { it["action"]?.jsonPrimitive?.content == "allow" }
        .map { it["permission"]!!.jsonPrimitive.content }

    @Test
    fun `its model comes from the chat provider, the provider's default when none is named`() = runTest {
        testMissionRepository(engine()).startChat("Bonjour", connectors = emptyList())

        val model = bodyOf(isPost("/session/ses_chat/prompt_async"))["model"]!!.jsonObject
        assertEquals("hobbitton-chat", model["providerID"]?.jsonPrimitive?.content)
        assertEquals("claude-sonnet-5", model["modelID"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a model picked on the gateway is moved onto the chat provider`() = runTest {
        testMissionRepository(engine()).startChat(
            "Bonjour",
            connectors = emptyList(),
            model = EngineModelRef(providerId = "hobbitton-gateway", modelId = "gpt-5.5"),
        )

        val model = bodyOf(isPost("/session/ses_chat/prompt_async"))["model"]!!.jsonObject
        assertEquals("hobbitton-chat", model["providerID"]?.jsonPrimitive?.content)
        assertEquals("gpt-5.5", model["modelID"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the new session is recorded as a chat`() = runTest {
        val kinds = InMemorySessionKinds()
        testMissionRepository(engine(), kinds = kinds).startChat("Bonjour", connectors = emptyList())

        assertEquals(EngineSessionKind.CHAT, kinds.recorded["ses_chat"])
    }

    @Test
    fun `a conversation started with files sends the files before the words`() = runTest {
        val photo = EnginePromptPart.file(mime = "image/jpeg", dataUrl = "data:image/jpeg;base64,AA==", filename = "a.jpg")

        testMissionRepository(engine()).startChat("Classe ce reçu", connectors = emptyList(), files = listOf(photo))

        val parts = bodyOf(isPost("/session/ses_chat/prompt_async"))["parts"]!!.jsonArray
        assertEquals(listOf("file", "text"), parts.map { it.jsonObject["type"]?.jsonPrimitive?.content })
    }

    @Test
    fun `every turn names the chat agent and provider, a former task's and a run's too`() = runTest {
        messagesBySession = mapOf(
            "ses_task" to """[{"info":{"id":"m1","role":"user","agent":"mission"},"parts":[]}]""",
        )
        val repository = testMissionRepository(engine())

        for (session in listOf("ses_chat", "ses_task", "ses_run")) {
            repository.sendMessage(
                sessionId = session,
                text = "et ensuite ?",
                model = EngineModelRef(providerId = "hobbitton-gateway", modelId = "claude-sonnet-5"),
            )
            val body = bodyOf(isPost("/session/$session/message"))
            assertEquals("chat", body["agent"]?.jsonPrimitive?.content, session)
            assertEquals("hobbitton-chat", body["model"]!!.jsonObject["providerID"]?.jsonPrimitive?.content, session)
        }
        // No transcript is read to pick an agent any more.
        assertTrue(sent.none { it.method == HttpMethod.Get && it.url.encodedPath.endsWith("/message") })
    }

    @Test
    fun `a turn without a picked model names the chat provider's default`() = runTest {
        testMissionRepository(engine()).sendMessage(sessionId = "ses_task", text = "Go ssh")

        val model = bodyOf(isPost("/session/ses_task/message"))["model"]!!.jsonObject
        assertEquals("hobbitton-chat", model["providerID"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the picker offers the chat provider only`() = runTest {
        val choice = testMissionRepository(engine()).models()

        assertEquals(setOf("hobbitton-chat"), choice.models.map { it.providerId }.toSet())
        assertEquals("hobbitton-chat", choice.preselected?.providerId)
    }

    @Test
    fun `the drawer lists the sessions started by hand, never a run of the scheduler`() = runTest {
        sessionsJson = """[
            {"id":"ses_here","title":"Une conversation d'ici","time":{"updated":400}},
            {"id":"ses_other","title":"Une conversation d'ailleurs","time":{"updated":300}},
            {"id":"ses_run","title":"veille — 2026-09-23T06:30+0200","time":{"updated":200}},
            {"id":"ses_bench","title":"traiter-une-reponse — manuel reprise 2 (à blanc)","time":{"updated":150}},
            {"id":"ses_mission","title":"Ranger les factures","time":{"updated":100}}
        ]"""
        messagesBySession = mapOf(
            "ses_other" to """[{"info":{"id":"m1","role":"user","agent":"chat"},"parts":[]}]""",
            "ses_mission" to """[{"info":{"id":"m2","role":"user","agent":"mission"},"parts":[]}]""",
        )
        val kinds = InMemorySessionKinds(mapOf("ses_here" to EngineSessionKind.CHAT))
        val repository = testMissionRepository(engine(), kinds = kinds)

        val chats = repository.recentChats()
        // 16/10/2026: the runs moved to the Conversations page, dry runs of the bench included.
        assertEquals<List<String>>(listOf("ses_here", "ses_other", "ses_mission"), chats.map { it.sessionId })
        assertTrue(chats.none { it.scheduled })
        // What was learned from a transcript is kept, so it is never read again.
        assertEquals(EngineSessionKind.CHAT, kinds.recorded["ses_other"])
        assertEquals(EngineSessionKind.TASK, kinds.recorded["ses_mission"])
    }

    @Test
    fun `the conversations page lists every session, a run under its mission's name`() = runTest {
        sessionsJson = """[
            {"id":"ses_here","title":"Une conversation d'ici","time":{"updated":400}},
            {"id":"ses_run","title":"veille — 2026-09-23T06:30+0200","time":{"updated":200}}
        ]"""
        val kinds = InMemorySessionKinds(mapOf("ses_here" to EngineSessionKind.CHAT))

        val chats = testMissionRepository(engine(), kinds = kinds).recentChats(scope = ChatScope.ALL)

        assertEquals<List<String>>(listOf("ses_here", "ses_run"), chats.map { it.sessionId })
        assertEquals("Veille", chats.single { it.scheduled }.title)
    }

    @Test
    fun `a search reaches every session, past the page, accents folded`() = runTest {
        // 21/10/2026: the drawer's search filtered only the rows drawn, and « energie » did not find
        // « Énergie du Pic ».
        sessionsJson = """[
            {"id":"ses_new","title":"Une conversation récente","time":{"updated":900}},
            {"id":"ses_mid","title":"Autre chose","time":{"updated":500}},
            {"id":"ses_old","title":"À décider · Énergie du Pic en congés","time":{"updated":10}}
        ]"""
        val kinds = InMemorySessionKinds(
            listOf("ses_new", "ses_mid", "ses_old").associateWith { EngineSessionKind.CHAT },
        )
        val repository = testMissionRepository(engine(), kinds = kinds)

        assertEquals<List<String>>(
            listOf("ses_new", "ses_mid"),
            repository.recentChats(scope = ChatScope.ALL, limit = 2).map { it.sessionId },
        )
        assertEquals<List<String>>(
            listOf("ses_old"),
            repository.recentChats(scope = ChatScope.ALL, limit = 2, query = "energie DU pic").map { it.sessionId },
        )
    }

    @Test
    fun `the search fold lowers case and drops accents`() {
        assertEquals("a decider · energie du pic, coeur", "À décider · Énergie du Pic, cœur".foldForSearch())
        assertTrue("Relance énergie".matchesSearch("ENERGIE"))
        assertTrue("Relance énergie".matchesSearch("  "))
        assertTrue(!"Relance énergie".matchesSearch("garage"))
    }

    @Test
    fun `unread come first, and a run is unread only when it had something to say`() = runTest {
        sessionsJson = """[
            {"id":"ses_quiet","title":"guet — 2026-10-16T17:30+0200","time":{"updated":900}},
            {"id":"ses_seen","title":"Une conversation lue","time":{"updated":800}},
            {"id":"ses_news","title":"guet — 2026-10-16T17:00+0200","time":{"updated":700}},
            {"id":"ses_old","title":"Avant le décompte","time":{"updated":50}},
            {"id":"ses_reply","title":"Une réponse arrivée","time":{"updated":600}},
            {"id":"ses_failed","title":"brief — 2026-10-16T06:45+0200","time":{"updated":500}}
        ]"""
        runsJson = """[
            {"session":"ses_quiet","mission":"guet","succes":true,"incomplet":false,"a_signaler":false},
            {"session":"ses_news","mission":"guet","succes":true,"incomplet":false,"a_signaler":true},
            {"session":"ses_failed","mission":"brief","succes":false,"incomplet":false,"a_signaler":true},
            {"session":"ses_failed","mission":"brief","succes":false,"incomplet":false,"a_signaler":null}
        ]"""
        val kinds = InMemorySessionKinds(
            listOf("ses_seen", "ses_old", "ses_reply").associateWith { EngineSessionKind.CHAT },
        )
        val reads = InMemoryConversationReads(floorMillis = 100, seen = mapOf("ses_seen" to 850))

        val chats = testMissionRepository(engine(), kinds = kinds, reads = reads).recentChats(scope = ChatScope.ALL)

        assertEquals<List<String>>(
            listOf("ses_news", "ses_reply", "ses_failed", "ses_quiet", "ses_seen", "ses_old"),
            chats.map { it.sessionId },
        )
        assertEquals(listOf(true, true, true, false, false, false), chats.map { it.unread })
    }

    @Test
    fun `a conversation marked read is no longer unread`() = runTest {
        sessionsJson = """[{"id":"ses_reply","title":"Une réponse arrivée","time":{"updated":600}}]"""
        val kinds = InMemorySessionKinds(mapOf("ses_reply" to EngineSessionKind.CHAT))
        val reads = InMemoryConversationReads(floorMillis = 100)
        val repository = testMissionRepository(engine(), kinds = kinds, reads = reads)

        assertTrue(repository.recentChats().single().unread)
        repository.markRead("ses_reply", atMillis = 600)
        assertTrue(!repository.recentChats().single().unread)
    }

    @Test
    fun `a conversation whose question the scheduler withdrew reads as read`() = runTest {
        sessionsJson = """[
            {"id":"ses_moot","title":"À décider · relance du maçon","time":{"updated":700}},
            {"id":"ses_open","title":"À décider · devis","time":{"updated":650}}
        ]"""
        serverReadsJson = """{"ses_moot":700,"ses_ailleurs":900}"""
        val kinds = InMemorySessionKinds(listOf("ses_moot", "ses_open").associateWith { EngineSessionKind.CHAT })
        val reads = InMemoryConversationReads(floorMillis = 100, seen = mapOf("ses_moot" to 200))

        val chats = testMissionRepository(engine(), kinds = kinds, reads = reads).recentChats(scope = ChatScope.ALL)

        assertEquals(mapOf("ses_open" to true, "ses_moot" to false), chats.associate { it.sessionId to it.unread })
    }

    @Test
    fun `a later local mark is not pulled back by the scheduler's`() {
        val snapshot = ReadSnapshot(floorMillis = 0, seen = mapOf("ses_a" to 900))
        assertEquals(900L, snapshot.withServerReads(mapOf("ses_a" to 500)).seen["ses_a"])
        assertTrue(!snapshot.withServerReads(mapOf("ses_b" to 500)).isUnread("ses_b", 400))
    }

    @Test
    fun `a session the scheduler titled is never read to be classified`() = runTest {
        sessionsJson = """[{"id":"ses_run","title":"veille — manuel","time":{"updated":1}}]"""

        val chats = testMissionRepository(engine()).recentChats(scope = ChatScope.ALL)

        assertTrue(sent.none { it.url.encodedPath == "/session/ses_run/message" })
        assertEquals("Veille", chats.single().title)
    }
}
