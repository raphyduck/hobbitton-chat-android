package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.network.di.librechatJson
import com.garfiec.librechat.core.network.engine.EngineHttpException
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * The MCP envelope, against payloads shaped like the scheduler's own.
 *
 * The scheduler answers a tool call with a JSON-RPC envelope whose useful part is a *string* buried
 * two levels down, and that string is itself JSON. Three layers is two more than anyone remembers
 * correctly, which is why each one is pinned here rather than trusted.
 */
class SchedulerApiTest {

    private val json = librechatJson

    private fun api(engine: MockEngine): SchedulerApi = SchedulerApi(
        HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            defaultRequest {
                url("https://sched.example.com")
                contentType(ContentType.Application.Json)
            }
        },
        json,
    )

    private fun envelope(payload: String): String =
        """{"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":${
            json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(),
                kotlinx.serialization.json.JsonPrimitive(payload))
        }}]}}"""

    private val etat = """
        {"missions":[{"nom":"sante-serveur-mcp","profil":"veille","active":true,
        "cron":"10 6 * * *","quand":null,"fuseau":"Europe/Paris",
        "prochaine":"2026-08-24T08:10+02:00","connecteurs":["fichiers","imap"],
        "outils_declares":7,"modele":null,"timeout_s":900,"budget_tokens":700000,
        "plafond_appels":12,"notifier":true,"en_cours":false,
        "derniere":{"debut":"2026-08-23T10:23:08+00:00","fin":"2026-08-23T10:24:57+00:00",
        "duree_s":108.5,"jetons":216775,"arret":"terminée","succes":true,
        "session":"ses_fd1dabdf1ffe2Nqh05ztfujtyv"}}]}
    """.trimIndent().replace("\n", "")

    @Test
    fun `a mission is read through all three layers`() = runTest {
        val api = api(MockEngine {
            respond(
                content = envelope(etat),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })

        val mission = api.state().missions.single()

        assertThat(mission.name).isEqualTo("sante-serveur-mcp")
        assertThat(mission.enabled).isTrue()
        assertThat(mission.cron).isEqualTo("10 6 * * *")
        assertThat(mission.nextRun).isEqualTo("2026-08-24T08:10+02:00")
        assertThat(mission.declaredTools).isEqualTo(7)
        assertThat(mission.toolCallCeiling).isEqualTo(12)
        assertThat(mission.lastRun?.tokens).isEqualTo(216_775)
        assertThat(mission.lastRun?.succeeded).isTrue()
    }

    /**
     * The edge may negotiate `text/event-stream` instead of JSON. Accepting only JSON works until
     * the day it doesn't, and then the list is empty with nothing in the logs to say why.
     */
    @Test
    fun `an event-stream answer is read like a JSON one`() = runTest {
        val api = api(MockEngine {
            respond(
                content = "event: message\ndata: ${envelope(etat)}\n\n",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString()),
            )
        })

        assertThat(api.state().missions.single().name).isEqualTo("sante-serveur-mcp")
    }

    /**
     * A running mission has `succes: null`, not `false`. Rendering that as a failure would show a
     * red banner on a mission that is working — the reason the field is nullable at all.
     */
    @Test
    fun `a running mission is neither a success nor a failure`() = runTest {
        val payload = """
            {"missions":[{"nom":"rapprochement-qonto","profil":"work-compta","active":true,
            "cron":"0 5 * * *","fuseau":"Europe/Paris","en_cours":true,
            "derniere":{"debut":"2026-08-23T05:00:08+00:00","fin":null,"duree_s":null,
            "jetons":null,"arret":null,"succes":null,"session":"ses_x"}}]}
        """.trimIndent().replace("\n", "")
        val api = api(MockEngine {
            respond(
                content = envelope(payload),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })

        val mission = api.state().missions.single()
        assertThat(mission.running).isTrue()
        assertThat(mission.lastRun?.succeeded).isNull()
    }

    /**
     * A JSON-RPC error is a **200** with an `error` member. Reading `result` first would throw a
     * « missing key » naming nothing, on a response that says exactly what went wrong.
     */
    @Test
    fun `a protocol error is reported with its own message`() = runTest {
        val api = api(MockEngine {
            respond(
                content = """{"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"mission introuvable"}}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })

        val failure = assertFailsWith<EngineHttpException> { api.state() }
        assertThat(failure.message).contains("mission introuvable")
    }

    /**
     * The three states of a spend line, which must never be confusable: a real amount, a real
     * amount too small to print, and no price at all. The wire carries the difference as
     * `depense: null` against a number — never as a zero.
     */
    @Test
    fun `an unpriced model arrives as null and not as zero`() = runTest {
        val payload = """
            {"jours":[{"jour":"2026-08-23","depense":13.0373135,"jetons":7749493,
            "complet":false,"modeles":[]}],
            "modeles":[{"modele":"anthropic/claude-sonnet-5","jetons":7748977,
            "jetons_entree":7452847,"jetons_sortie":296130,"jetons_cache":3719970,
            "depense":13.0373135,"tarife":true,"appels":173,"echecs":0},
            {"modele":"openai/deepseek-chat","jetons":516,"jetons_entree":295,
            "jetons_sortie":221,"jetons_cache":0,"depense":null,"tarife":false,
            "appels":5,"echecs":0}],
            "depense_totale":13.0373135,"jetons_totaux":7749493,
            "modeles_non_tarifes":["openai/deepseek-chat"],"economie_du_cache":6.6959}
        """.trimIndent().replace("\n", "")
        val api = api(MockEngine {
            respond(
                content = envelope(payload),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })

        val report = api.consumption(days = 7)

        assertThat(report.isComplete).isFalse()
        assertThat(report.cacheSavings).isEqualTo(6.6959)
        val byModel = report.models.associateBy { it.model }
        assertThat(byModel.getValue("openai/deepseek-chat").spend).isNull()
        assertThat(byModel.getValue("openai/deepseek-chat").isPriced).isFalse()
        // Full precision on the wire: rounding would make a tiny priced amount indistinguishable
        // from an unpriced one, which is the whole thing this feature exists to prevent.
        assertThat(byModel.getValue("anthropic/claude-sonnet-5").spend).isEqualTo(13.0373135)
    }

    /**
     * The scheduler answers `{"erreur": …}` when its own gateway did not reply. Decoding that as a
     * report would raise a « missing key » naming nothing, on an answer that says what went wrong.
     */
    @Test
    fun `a gateway failure is reported with the scheduler's own words`() = runTest {
        val api = api(MockEngine {
            respond(
                content = envelope("""{"erreur":"GET /user/daily/activity : timeout"}"""),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })

        val failure = assertFailsWith<EngineHttpException> { api.consumption() }
        assertThat(failure.message).contains("timeout")
    }

    @Test
    fun `the period is sent as a tool argument`() = runTest {
        lateinit var sent: String
        val api = api(MockEngine { request ->
            sent = String(request.body.toByteArray())
            respond(
                content = envelope("""{"jours":[],"modeles":[],"depense_totale":0.0,
                    "jetons_totaux":0,"modeles_non_tarifes":[],"economie_du_cache":0.0}""".trimIndent().replace("\n", "")),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })

        api.consumption(days = 30)

        val arguments = json.parseToJsonElement(sent).jsonObject
            .getValue("params").jsonObject.getValue("arguments").jsonObject
        assertThat(arguments.getValue("jours").jsonPrimitive.content).isEqualTo("30")
        // Without this the tool answers the prose a person wants in a chat, and decoding fails.
        assertThat(arguments.getValue("json_brut").jsonPrimitive.content).isEqualTo("true")
    }

    @Test
    fun `running a mission sends its name as a tool argument`() = runTest {
        lateinit var sent: String
        val api = api(MockEngine { request ->
            sent = String(request.body.toByteArray())
            respond(
                content = envelope("mission « brief-crypto » lancée, session ses_y."),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })

        val answer = api.run("brief-crypto")

        val params = json.parseToJsonElement(sent).jsonObject.getValue("params").jsonObject
        assertThat(params.getValue("name").jsonPrimitive.content).isEqualTo("lancer")
        assertThat(
            params.getValue("arguments").jsonObject.getValue("nom").jsonPrimitive.content,
        ).isEqualTo("brief-crypto")
        // The scheduler's own sentence is what gets shown: it names the session, or says why it
        // refused. Swallowing it would leave a button that does nothing visible.
        assertThat(answer).contains("ses_y")
    }
}
