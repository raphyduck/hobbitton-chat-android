package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.engine.CreateEngineSessionRequest
import com.garfiec.librechat.core.model.engine.EngineAgentProfile
import com.garfiec.librechat.core.model.engine.EngineMessage
import com.garfiec.librechat.core.model.engine.EnginePermissionReply
import com.garfiec.librechat.core.model.engine.EnginePromptPart
import com.garfiec.librechat.core.model.engine.EnginePromptRequest
import com.garfiec.librechat.core.model.engine.EngineProviderCatalogue
import com.garfiec.librechat.core.model.engine.EngineSession
import com.garfiec.librechat.core.model.engine.EngineSessionStatus
import com.garfiec.librechat.core.network.engine.EngineHttpException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.http.path

/**
 * The Agent engine, spoken to **directly** — no façade in front of it (server-side D-026).
 *
 * Nine routes out of the engine's 162. The full generated client lives server-side under
 * `clients/kotlin`, regenerated from the engine's own OpenAPI on every upgrade so that a breaking
 * change shows up as a reviewable diff; carrying its 2 000 files into a KMP app to call nine
 * routes would cost far more than it explains. This class is written against that contract.
 *
 * It takes its own [HttpClient] — qualifier `KoinQualifiers.Engine` — because the engine is a
 * different host with different credentials from LibreChat: its own Basic auth, plus the Authelia
 * bearer that the reverse proxy demands. Reusing the LibreChat client would send the chat's session
 * cookie to a host that has no idea what to do with it, and none of the credentials the engine
 * actually wants.
 */
class AgentEngineApi(
    private val client: HttpClient,
) {

    /**
     * Every session the engine knows about, newest last. This is the list the tab is built from:
     * the engine is the source of truth for missions, and nothing is cached locally — a mission
     * that exists only on a phone is a mission nobody can supervise.
     */
    suspend fun sessions(): List<EngineSession> =
        client.get { url { path("session") } }.decoded()

    /** The profiles the engine is configured with — `cerveau`, `work-compta`… */
    suspend fun profiles(): List<EngineAgentProfile> =
        client.get { url { path("agent") } }.decoded()

    /**
     * The providers this engine is wired to, and the models each one offers.
     *
     * `config/providers`, **not** `provider`. Both answer 200 and both look right; the second
     * returns the engine's whole built-in catalogue — 5,5 MB against the live server on
     * 28/08/2026, versus 11,8 kB here — most of it providers this deployment holds no key for.
     */
    suspend fun providers(): EngineProviderCatalogue =
        client.get { url { path("config/providers") } }.decoded()

    suspend fun createSession(request: CreateEngineSessionRequest): EngineSession =
        client.post {
            url { path("session") }
            setBody(request)
        }.decoded()

    /**
     * Hands the objective over and returns at once. Watching what happens next is [status] and
     * [messages] — or, in interactive mode, the event stream.
     */
    suspend fun prompt(sessionId: String, request: EnginePromptRequest) {
        client.post {
            url { path("session/${sessionId.encodeURLPathPart()}/prompt_async") }
            setBody(request)
        }.orThrow()
    }

    /**
     * Talks to an existing session and **waits for the answer** — the interactive chat's send.
     *
     * `POST /session/{id}/message`, the classic route, deliberately and not the v2 prompt. A mission
     * is launched through `prompt_async`, and a session that has run there never executes a v2
     * prompt: it is admitted (`prompt.admitted`, `prompted`) and then no step ever starts, whatever
     * the `delivery` — measured on the live engine on 29/08/2026, which is exactly the « I send a
     * message and nothing answers » the tab shipped with. This route answers in seconds with the
     * finished assistant message, and streams its progress on the global `/event` feed meanwhile.
     */
    suspend fun sendMessage(sessionId: String, text: String, agent: String? = null): EngineMessage =
        client.post {
            url { path("session/${sessionId.encodeURLPathPart()}/message") }
            setBody(EnginePromptRequest(parts = listOf(EnginePromptPart(text = text)), agent = agent))
        }.decoded()

    /**
     * Every active session at once, keyed by id — there is no per-session route. A session **absent
     * from this map is idle**, which is why callers must not read absence as success on its own:
     * a session whose model call failed is also absent, milliseconds after it started. Pair this
     * with [messages] and look at `info.error`.
     */
    suspend fun status(): Map<String, EngineSessionStatus> =
        client.get { url { path("session/status") } }.decoded()

    suspend fun messages(sessionId: String): List<EngineMessage> =
        client.get {
            url { path("session/${sessionId.encodeURLPathPart()}/message") }
        }.decoded()

    /** Stops a running session. The engine expects **no body**; sending one is a 400. */
    suspend fun abort(sessionId: String) {
        client.post { url { path("session/${sessionId.encodeURLPathPart()}/abort") } }.orThrow()
    }

    /** Interactive mode: answer a permission the engine is waiting on. */
    suspend fun replyToPermission(
        sessionId: String,
        permissionId: String,
        reply: EnginePermissionReply,
    ) {
        client.post {
            url {
                path(
                    "session/${sessionId.encodeURLPathPart()}" +
                        "/permissions/${permissionId.encodeURLPathPart()}",
                )
            }
            setBody(reply)
        }.orThrow()
    }

    /** The working-tree changes a coding mission produced, as a unified diff. */
    suspend fun diff(sessionId: String): String =
        client.get {
            url { path("session/${sessionId.encodeURLPathPart()}/diff") }
        }.decoded()

    /**
     * Reads the body only once the status says there is one worth reading.
     *
     * Without this a 401 from the portal — HTML, not JSON — reaches the deserializer and surfaces as
     * `NoTransformationFoundException`, which names a Kotlin type instead of the status code that
     * explains everything. Seen on 22 August, on this exact route.
     */
    private suspend inline fun <reified T> HttpResponse.decoded(): T {
        orThrow()
        return body()
    }

    private fun HttpResponse.orThrow() {
        if (!status.isSuccess()) {
            throw EngineHttpException(
                status = status.value,
                method = request.method.value,
                path = request.url.encodedPath,
            )
        }
    }
}
