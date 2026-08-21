package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.engine.CreateEngineSessionRequest
import com.garfiec.librechat.core.model.engine.EngineAgentProfile
import com.garfiec.librechat.core.model.engine.EngineMessage
import com.garfiec.librechat.core.model.engine.EnginePermissionReply
import com.garfiec.librechat.core.model.engine.EnginePromptRequest
import com.garfiec.librechat.core.model.engine.EngineSession
import com.garfiec.librechat.core.model.engine.EngineSessionStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart
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
        client.get { url { path("session") } }.body()

    /** The profiles the engine is configured with — `cerveau`, `work-compta`… */
    suspend fun profiles(): List<EngineAgentProfile> =
        client.get { url { path("agent") } }.body()

    suspend fun createSession(request: CreateEngineSessionRequest): EngineSession =
        client.post {
            url { path("session") }
            setBody(request)
        }.body()

    /**
     * Hands the objective over and returns at once. Watching what happens next is [status] and
     * [messages] — or, in interactive mode, the event stream.
     */
    suspend fun prompt(sessionId: String, request: EnginePromptRequest) {
        client.post {
            url { path("session/${sessionId.encodeURLPathPart()}/prompt_async") }
            setBody(request)
        }
    }

    /**
     * Every active session at once, keyed by id — there is no per-session route. A session **absent
     * from this map is idle**, which is why callers must not read absence as success on its own:
     * a session whose model call failed is also absent, milliseconds after it started. Pair this
     * with [messages] and look at `info.error`.
     */
    suspend fun status(): Map<String, EngineSessionStatus> =
        client.get { url { path("session/status") } }.body()

    suspend fun messages(sessionId: String): List<EngineMessage> =
        client.get {
            url { path("session/${sessionId.encodeURLPathPart()}/message") }
        }.body()

    /** Stops a running session. The engine expects **no body**; sending one is a 400. */
    suspend fun abort(sessionId: String) {
        client.post { url { path("session/${sessionId.encodeURLPathPart()}/abort") } }
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
        }
    }

    /** The working-tree changes a coding mission produced, as a unified diff. */
    suspend fun diff(sessionId: String): String =
        client.get {
            url { path("session/${sessionId.encodeURLPathPart()}/diff") }
        }.body()
}
