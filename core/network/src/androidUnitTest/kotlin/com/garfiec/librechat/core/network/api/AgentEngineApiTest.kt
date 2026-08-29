package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.engine.CreateEngineSessionRequest
import com.garfiec.librechat.core.model.engine.EnginePermissionReply
import com.garfiec.librechat.core.model.engine.EnginePermissionRule
import com.garfiec.librechat.core.model.engine.EnginePromptPart
import com.garfiec.librechat.core.model.engine.EnginePromptRequest
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Wire-shape tests against payloads the real engine produced on `plex`. Each one guards a trap that
 * has already cost a debugging session on the server side — they are not hypotheses.
 */
class AgentEngineApiTest {

    private val json = librechatJson

    private fun api(engine: MockEngine): AgentEngineApi = AgentEngineApi(
        HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            defaultRequest {
                url("https://agent.example.com")
                contentType(ContentType.Application.Json)
            }
        },
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `session creation sends permissions as a list, not a map`() = runTest {
        val engine = MockEngine { request ->
            val body = json.parseToJsonElement(String(request.body.toByteArray())).jsonObject
            // The engine answers 400 with no usable message when this is an object. The assertion
            // lives here so the shape breaks a test rather than a mission at 3am.
            val rules = body["permission"]!!.jsonArray
            assertThat(rules).hasSize(2)
            assertThat(rules[0].jsonObject["permission"]!!.jsonPrimitive.content).isEqualTo("*")
            assertThat(rules[0].jsonObject["action"]!!.jsonPrimitive.content).isEqualTo("deny")
            // `pattern` MUST survive serialization even though it equals its default: the engine
            // validates each rule as {permission, pattern, action} and 400s the whole request when
            // it is absent. The app's Json is `encodeDefaults = false`, so only @EncodeDefault(ALWAYS)
            // on the field keeps it on the wire — dropping it made every mission launch fail.
            assertThat(rules[0].jsonObject.keys).contains("pattern")
            assertThat(rules[0].jsonObject["pattern"]!!.jsonPrimitive.content).isEqualTo("*")
            respond(
                content = """{"id":"ses_abc","title":"consolidation"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val session = api(engine).createSession(
            CreateEngineSessionRequest(
                agent = "cerveau",
                title = "consolidation",
                permission = listOf(
                    EnginePermissionRule(permission = "*", action = "deny"),
                    EnginePermissionRule(permission = "memoire_*", action = "allow"),
                ),
            ),
        )

        assertThat(session.id).isEqualTo("ses_abc")
    }

    @Test
    fun `the objective goes to prompt_async, which is the route that exists`() = runTest {
        var path: String? = null
        var parts: kotlinx.serialization.json.JsonArray? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            parts = json.parseToJsonElement(String(request.body.toByteArray()))
                .jsonObject["parts"]!!.jsonArray
            respond(content = "{}", status = HttpStatusCode.OK, headers = jsonHeaders())
        }

        api(engine).prompt(
            sessionId = "ses_abc",
            request = EnginePromptRequest(parts = listOf(EnginePromptPart(text = "fais le point"))),
        )

        // `message/async` reads plausibly and returns 404.
        assertThat(path).isEqualTo("/session/ses_abc/prompt_async")
        // `type` MUST survive serialization even at its default: prompt_async validates each part as
        // a discriminated union on `type` and 400s ("Expected { type: \"text\" … }") when it is
        // absent. Same `encodeDefaults = false` trap as the permission rule's `pattern` — the twin
        // bug one call further on. @EncodeDefault(ALWAYS) is what keeps it on the wire.
        assertThat(parts!![0].jsonObject.keys).contains("type")
        assertThat(parts!![0].jsonObject["type"]!!.jsonPrimitive.content).isEqualTo("text")
    }

    @Test
    fun `the streaming prompt hits the v2 route, nests the text, and unwraps the data envelope`() = runTest {
        var path: String? = null
        var body: kotlinx.serialization.json.JsonObject? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            body = json.parseToJsonElement(String(request.body.toByteArray())).jsonObject
            // The v2 routes answer inside a `data` envelope — unlike the classic ones.
            respond(
                content = """{"data":{"admittedSeq":1,"id":"msg_user","sessionID":"ses_abc"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val admission = api(engine).promptStreaming(sessionId = "ses_abc", text = "salut")

        // v2 lives under /api; the classic prompt_async does not. And the text nests under `prompt`.
        assertThat(path).isEqualTo("/api/session/ses_abc/prompt")
        assertThat(body!!["prompt"]!!.jsonObject["text"]!!.jsonPrimitive.content).isEqualTo("salut")
        // The id the engine minted for the user's message, lifted out of the envelope.
        assertThat(admission.id).isEqualTo("msg_user")
    }

    @Test
    fun `status returns every active session at once, and absence means idle`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"ses_running":{"type":"retry","attempt":5,
                    |"message":"Cannot connect to API"}}""".trimMargin(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val statuses = api(engine).status()

        assertThat(statuses).containsKey("ses_running")
        assertThat(statuses["ses_running"]!!.type).isEqualTo("retry")
        assertThat(statuses["ses_running"]!!.attempt).isEqualTo(5)
        // The one that matters: a session nobody reported on is idle, not missing.
        assertThat(statuses["ses_finished"]).isNull()
    }

    @Test
    fun `a failed model call is reported in the message, not by the session dying`() = runTest {
        // Captured verbatim on 21/08/2026: the gateway had lost its database, and the session fell
        // idle in 11 milliseconds having consumed nothing. Reading only the status map, this looks
        // exactly like a mission that finished.
        val engine = MockEngine {
            respond(
                content = """[{"info":{"id":"msg_1","role":"user"},
                    |"parts":[{"id":"p1","type":"text","text":"consolidation"}]},
                    |{"info":{"id":"msg_2","role":"assistant",
                    |"tokens":{"input":0,"output":0,"reasoning":0,"cache":{"read":0,"write":0}},
                    |"error":{"name":"UnknownError","data":{"message":"No connected db."}}},
                    |"parts":[{"type":"step-start"}]}]""".trimMargin(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val messages = api(engine).messages("ses_abc")

        val assistant = messages.last().info
        assertThat(assistant.error?.data?.message).isEqualTo("No connected db.")
        assertThat(assistant.tokens?.total).isEqualTo(0)
    }

    @Test
    fun `token total counts cache reads and writes too`() = runTest {
        val engine = MockEngine {
            respond(
                content = """[{"info":{"id":"m","role":"assistant","tokens":
                    |{"input":100,"output":20,"reasoning":5,"cache":{"read":1000,"write":50}}}}]"""
                    .trimMargin(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val tokens = api(engine).messages("ses_abc").single().info.tokens!!

        // A budget compared against input+output alone under-counts by the cache, which on a long
        // mission is most of the traffic.
        assertThat(tokens.total).isEqualTo(1175)
    }

    @Test
    fun `abort sends no body`() = runTest {
        var bodySize = -1
        val engine = MockEngine { request ->
            bodySize = request.body.toByteArray().size
            respond(content = "{}", status = HttpStatusCode.OK, headers = jsonHeaders())
        }

        api(engine).abort("ses_abc")

        // Posting a body here is a 400 from the engine.
        assertThat(bodySize).isEqualTo(0)
    }

    @Test
    fun `a permission reply names the permission it answers`() = runTest {
        var path: String? = null
        var body: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            body = String(request.body.toByteArray())
            respond(content = "{}", status = HttpStatusCode.OK, headers = jsonHeaders())
        }

        api(engine).replyToPermission("ses_abc", "perm_1", EnginePermissionReply(EnginePermissionReply.ONCE))

        assertThat(path).isEqualTo("/session/ses_abc/permissions/perm_1")
        assertThat(json.parseToJsonElement(body!!).jsonObject["response"]!!.jsonPrimitive.content)
            .isEqualTo("once")
    }
}
