package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.core.model.scheduler.Consumption
import com.garfiec.librechat.core.model.scheduler.ProviderHealth
import com.garfiec.librechat.core.model.scheduler.SchedulerState
import com.garfiec.librechat.core.network.engine.EngineHttpException
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The scheduler, spoken to in **MCP** — because that is the only language it speaks.
 *
 * The scheduler is a connector, not a REST service: the same tools serve claude.ai, LibreChat and
 * this app, and giving it a second HTTP surface for one client would mean two ways to change a
 * schedule and two chances to make them disagree. MCP over plain HTTP is a JSON-RPC POST; there is
 * no library needed for that, and none is worth carrying into a phone for four calls.
 *
 * Two details of that protocol, both measured server-side rather than guessed:
 *
 * - the server runs `stateless_http`, so there is **no `Mcp-Session-Id` to obtain or carry** — a
 *   bridge in front of it would drop the header anyway, which is exactly what broke the brain's
 *   connector on 20 August;
 * - a response may come back as JSON *or* as a `text/event-stream` frame depending on what the
 *   edge negotiates, hence [singleFrame]. Accepting only JSON works until the day it doesn't.
 */
class SchedulerApi(
    private val client: HttpClient,
    private val json: Json,
) {

    /** Every mission, its next due time and its last run — one call, because a screen wants it at once. */
    suspend fun state(): SchedulerState = json.decodeFromString(callTool("etat", null))

    /**
     * What the platform spent, by model and by day, over the last [days] days — both bounds
     * included, so `1` means today.
     *
     * `json_brut` is what makes this readable by a program: without it the tool answers the prose
     * a person wants in a chat. One tool serving both is deliberate — every tool a connector
     * declares is re-sent to the model on every turn, and a second one would be paid for on every
     * mission that never calls it.
     */
    suspend fun consumption(days: Int = 7): Consumption = decode(
        callTool("consommation", buildJsonObject {
            put("jours", days)
            put("json_brut", true)
        }),
        "consommation",
    )

    /**
     * Reads a tool's JSON answer, raising the scheduler's own words when it reports a failure.
     *
     * A gateway the scheduler could not reach answers `{"erreur": "…"}` rather than a report.
     * Decoding that into the expected type would raise a serialization error naming a missing key,
     * on an answer that says exactly what went wrong — so the cause is read out and raised the
     * same way a JSON-RPC error is. Shared by both gateway-backed tools: duplicating it would be
     * two places to forget the same thing.
     */
    private inline fun <reified T> decode(payload: String, tool: String): T {
        json.parseToJsonElement(payload).jsonObject["erreur"]?.let { reason ->
            throw EngineHttpException(200, tool, reason.jsonPrimitive.content)
        }
        return json.decodeFromString(payload)
    }

    /**
     * Which providers answer right now.
     *
     * **This performs a real call to every model in the catalogue.** Measured server-side on
     * 23/08/2026: 1.4 to 2.6 seconds and about $0.0015 for the ten of them. It is a verification,
     * not a refresh — callers must bind it to an explicit action, never to a screen appearing.
     */
    suspend fun providers(): ProviderHealth = decode(
        callTool("fournisseurs", buildJsonObject { put("json_brut", true) }),
        "fournisseurs",
    )

    /**
     * The connectors a mission may be given, and the exact tool names each one opens.
     *
     * Fetched rather than copied — see [ConnectorCatalogue] for the bug that cost. Cheap and
     * side-effect free: the scheduler builds it from a table it already holds in memory.
     */
    suspend fun connectors(): ConnectorCatalogue = decode(
        callTool("connecteurs", buildJsonObject { put("json_brut", true) }),
        "connecteurs",
    )

    /**
     * Starts a mission now. Returns the scheduler's own sentence, which is the useful thing to
     * show: it names the session, or says why it refused — « la mission tourne déjà », most often.
     */
    suspend fun run(name: String): String = callTool("lancer", buildJsonObject { put("nom", name) })

    suspend fun enable(name: String): String =
        callTool("activer", buildJsonObject { put("nom", name) })

    suspend fun disable(name: String): String =
        callTool("desactiver", buildJsonObject { put("nom", name) })

    /**
     * Changes one or two fields of a scheduled mission, leaving the rest untouched.
     *
     * `modifier`, deliberately **not** `planifier`: the latter replaces the whole mission, so
     * changing an hour would mean resending everything — including the prompt and the tool list,
     * which `etat` does not publish. This app would therefore wipe the prompt on the first
     * reschedule, silently. Server-side `modifier` merges instead.
     *
     * Null means « leave it alone ». Setting `cron` clears a one-shot's date and vice versa: a
     * mission is recurring or one-shot, never both.
     */
    suspend fun updateMission(
        name: String,
        cron: String? = null,
        runAt: String? = null,
        timeZone: String? = null,
        model: String? = null,
        connectors: List<String>? = null,
        toolCallCeiling: Int? = null,
        timeoutSeconds: Int? = null,
        tokenBudget: Int? = null,
        notifies: Boolean? = null,
    ): String = callTool(
        "modifier",
        buildJsonObject {
            put("nom", name)
            cron?.let { put("cron", it) }
            runAt?.let { put("quand", it) }
            timeZone?.let { put("fuseau", it) }
            model?.let { put("modele", it) }
            connectors?.let { list -> putJsonArray("connecteurs") { list.forEach { add(it) } } }
            toolCallCeiling?.let { put("plafond_appels", it) }
            timeoutSeconds?.let { put("timeout_s", it) }
            tokenBudget?.let { put("budget_tokens", it) }
            notifies?.let { put("notifier", it) }
        },
    )

    /**
     * Removes a scheduled mission. **Its history survives** — that is the record of what ran, and
     * it must not disappear with the schedule.
     */
    suspend fun deleteMission(name: String): String =
        callTool("supprimer", buildJsonObject { put("nom", name) })

    private suspend fun callTool(tool: String, arguments: JsonObject?): String {
        val envelope = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "tools/call")
            putJsonObject("params") {
                put("name", tool)
                put("arguments", arguments ?: JsonObject(emptyMap()))
            }
        }
        val response = client.post {
            url { path("mcp") }
            // Both, in this order: the server picks, and refusing the stream outright earns a 406.
            accept(ContentType.Application.Json)
            accept(ContentType.Text.EventStream)
            setBody(envelope)
        }
        return response.text(tool)
    }

    private suspend fun HttpResponse.text(tool: String): String {
        if (!status.isSuccess()) {
            throw EngineHttpException(status.value, request.url.toString(), bodyAsText())
        }
        val payload = json.parseToJsonElement(singleFrame(bodyAsText())).jsonObject

        // A JSON-RPC error is a 200 with an `error` member. Reading `result` first would throw a
        // « missing key » that names nothing, on a response that says precisely what went wrong.
        payload["error"]?.let { error ->
            val message = error.jsonObject["message"]?.jsonPrimitive?.content ?: error.toString()
            throw EngineHttpException(status.value, request.url.toString(), "$tool: $message")
        }

        return payload.getValue("result").jsonObject.getValue("content").jsonArray
            .joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
    }

    private companion object {
        /**
         * The payload of an event-stream answer, or the body unchanged when it is plain JSON.
         *
         * One frame is all this protocol sends for a tool call, so the first `data:` line is the
         * answer; anything else in the stream is framing.
         */
        fun singleFrame(body: String): String =
            if ("data: " in body) {
                body.lineSequence().first { it.startsWith("data: ") }.removePrefix("data: ")
            } else {
                body
            }
    }
}
