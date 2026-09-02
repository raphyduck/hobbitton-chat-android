package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.engine.CreateEngineSessionRequest
import com.garfiec.librechat.core.model.engine.EngineMessage
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EnginePermissionRule
import com.garfiec.librechat.core.model.engine.EnginePromptPart
import com.garfiec.librechat.core.model.engine.EnginePromptRequest
import com.garfiec.librechat.core.model.engine.EngineProviderCatalogue
import com.garfiec.librechat.core.model.engine.EngineSession
import com.garfiec.librechat.core.model.engine.EngineSessionPatch
import com.garfiec.librechat.core.model.engine.EngineSessionStatus
import com.garfiec.librechat.core.network.engine.EngineHttpException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.patch
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

    /**
     * One session, with the ruleset it currently carries.
     *
     * The list route does not serve `permission`; this one does, and it is the only place a live
     * session's grants are written down. Reading it is what lets the conversation say what the
     * mission can reach rather than what the screen happens to have ticked.
     */
    suspend fun session(sessionId: String): EngineSession =
        client.get { url { path("session/${sessionId.encodeURLPathPart()}") } }.decoded()

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
    suspend fun sendMessage(
        sessionId: String,
        text: String,
        model: EngineModelRef? = null,
        // Attached files ride as sibling parts of the same message — the engine has no upload
        // route, each part carries its bytes as a data URL. Files FIRST, text last: that is the
        // order OpenCode's own web UI sends, and the prose reads as commentary on the images
        // rather than the other way round.
        files: List<EnginePromptPart> = emptyList(),
        // The global profile's instructions. Sent on EVERY turn, not just the first: the engine
        // records the string on the user message it creates, so a turn without it is a turn that
        // did not hear the profile — and the profile can change between two messages of the same
        // session. See `EnginePromptRequest.system` for where it lands in the engine's prompt.
        system: String? = null,
    ): EngineMessage =
        client.post {
            url { path("session/${sessionId.encodeURLPathPart()}/message") }
            // No request cap on THIS route, and on this route only. The client's 30 s is right for
            // the small calls around it and wrong here: this one waits for the whole turn, tools
            // included, and a mission turn runs for minutes. What the user saw was the timeout, not
            // the engine — « The engine did not answer » under an answer that was streaming in on
            // the feed at that very moment (reported 30/08/2026). The engine writes nothing on this
            // socket until the turn is finished, so a read timeout would be just as wrong; the Stop
            // button, not a clock, is what ends a turn that has gone on too long.
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
                socketTimeoutMillis = Long.MAX_VALUE
            }
            setBody(
                EnginePromptRequest(
                    // The text part is omitted when there is nothing to say: a photo can be the
                    // whole message, and an empty text part is a shape the engine may well reject.
                    parts = files + listOfNotNull(
                        text.takeIf { it.isNotBlank() }?.let { EnginePromptPart.text(it) },
                    ),
                    // Per message, not per session: the route takes `{providerID, modelID}` and the
                    // engine has no « set the session's model » on the classic surface. Null means
                    // « whatever the session already runs on » — an absent key, not an empty one.
                    model = model,
                    system = system,
                ),
            )
        }.decoded()

    /**
     * Changes what a **live** session is allowed to touch.
     *
     * `PATCH /session/{id}` takes a whole `PermissionRuleset` and **appends** it — it does not
     * replace what the session already carries. Measured 31/08/2026 on a live mission: 1 016 rules
     * in 21 stacked blocks, one per call, and the list grows without bound over a long-lived
     * session. **Whether unticking actually revokes is therefore unverified**: a narrower block
     * omits the tool rather than denying it, so the answer hangs on which rule the engine keeps —
     * the last that matches, or the most specific — and this project asserts both in different
     * places. Settling it needs a measurement nobody has made: revoke a tool, then call it. Until
     * then a reader takes only the last block (`connectorsGranted`), which errs towards reporting
     * less than the session may hold.
     *
     * That the engine accepts this at all is what lets the conversation offer connector chips:
     * permissions are not frozen at creation, and a session that was launched with memory alone can
     * be handed the mail connector without being restarted and losing its transcript. The other
     * face of the same coin, seen the same day: a mission the scheduler had capped at 14 tools was
     * carrying 112 by the time someone had finished ticking boxes in front of it.
     *
     * The rules are the caller's to build, from the scheduler's catalogue — this class copies no
     * tool table of its own.
     */
    suspend fun setPermissions(sessionId: String, rules: List<EnginePermissionRule>) {
        client.patch {
            url { path("session/${sessionId.encodeURLPathPart()}") }
            setBody(EngineSessionPatch(permission = rules))
        }.orThrow()
    }

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
