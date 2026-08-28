package com.garfiec.librechat.core.model.engine

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The Agent engine (OpenCode) — not LibreChat. Its wire shapes come from the engine's own OpenAPI
 * document, versioned server-side as `clients/openapi.json`; these classes cover the eight routes
 * the Tasks tab actually uses, out of 162.
 *
 * Two traps are baked into the types below, both learned the hard way while the server-side
 * scheduler was written against this same API:
 *
 *  * a session's permissions are a **list** of rules, not a map — posting a map returns 400 with no
 *    hint as to which field is wrong;
 *  * there is **no per-session status route**. [EngineSessionStatus] is what `GET /session/status`
 *    returns for *every* active session at once, keyed by id, and a session that is absent from
 *    that map is idle — which is indistinguishable from "never started" unless you also read the
 *    messages. See [EngineMessageInfo.error].
 */
@Serializable
data class EngineSession(
    val id: String,
    val title: String? = null,
    val version: String? = null,
    val time: EngineTime? = null,
)

@Serializable
data class EngineTime(
    val created: Long? = null,
    val updated: Long? = null,
    val completed: Long? = null,
)

/**
 * One permission rule. `permission` is a tool name pattern (`bash`, `edit`, `memoire_*`, `*`),
 * `pattern` narrows it further (`*` for all), `action` is `allow`, `ask` or `deny`.
 *
 * Order does not decide the outcome — the engine takes the most specific match — so a rule list
 * always opens with a `*` → `deny` catch-all and adds the connectors the mission is allowed.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class EnginePermissionRule(
    val permission: String,
    // ALWAYS on the wire, even though it equals its default: the engine's `POST /session` validates
    // each rule as `{permission, pattern, action}` and rejects the whole request with a bare 400
    // `{"_tag":"BadRequest"}` when `pattern` is absent (verified against the live engine 28/08/2026).
    // The app's Json is `encodeDefaults = false`, so without this annotation the default `"*"` is
    // dropped and EVERY mission launch fails — permissionsFor always emits at least the deny-all rule
    // with the default pattern, so createSession never once succeeded from the app.
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val pattern: String = "*",
    val action: String,
)

@Serializable
data class EngineAgentProfile(
    val name: String,
    val description: String? = null,
    val model: EngineModelRef? = null,
    /** Iteration cap. NOT a duration: the engine has no wall-clock bound, which is why the server
     * carries a watchdog instead (server-side D-028). */
    val steps: Int? = null,
    /** `primary`, `subagent` or `all` — a subagent is the engine's internal help, not a profile. */
    val mode: String? = null,
    /**
     * True for the agents OpenCode ships with — `build`, `compaction` — as opposed to the ones
     * declared in the deployment's own configuration. On 25/08 the New-mission sheet offered
     * `compaction`, the engine's internal context-summarizer, as if it were a mission profile:
     * the list from `GET /agent` mixes both kinds, and only this flag tells them apart.
     */
    val native: Boolean = false,
    val hidden: Boolean = false,
)

@Serializable
data class EngineModelRef(
    @SerialName("providerID") val providerId: String,
    @SerialName("modelID") val modelId: String,
)

/**
 * An entry of the `GET /session/status` map. `type` is the engine's own vocabulary — `idle`,
 * `running`, `retry`… — and the shape carries whatever the state needs, so everything past `type`
 * is optional by construction.
 */
@Serializable
data class EngineSessionStatus(
    val type: String,
    val message: String? = null,
    val attempt: Int? = null,
)

@Serializable
data class EngineMessage(
    val info: EngineMessageInfo,
    val parts: List<EnginePart> = emptyList(),
)

@Serializable
data class EngineMessageInfo(
    val id: String,
    @SerialName("sessionID") val sessionId: String? = null,
    val role: String,
    val time: EngineTime? = null,
    val agent: String? = null,
    val tokens: EngineTokens? = null,
    val cost: Double? = null,
    /**
     * A failed model call does not crash the session: the engine files the error here and lets the
     * session fall idle within milliseconds. Reading only the status map therefore reports success
     * for a mission that never spoke to a model — the exact failure that silently swallowed a
     * nightly run on 21/08/2026 (server-side D-032).
     */
    val error: EngineError? = null,
)

@Serializable
data class EngineError(
    val name: String? = null,
    val data: EngineErrorData? = null,
)

@Serializable
data class EngineErrorData(
    val message: String? = null,
)

@Serializable
data class EngineTokens(
    val input: Long = 0,
    val output: Long = 0,
    val reasoning: Long = 0,
    val cache: EngineCacheTokens? = null,
) {
    /** What the mission actually consumed, cache included — the number a budget is compared to. */
    val total: Long
        get() = input + output + reasoning + (cache?.read ?: 0) + (cache?.write ?: 0)
}

@Serializable
data class EngineCacheTokens(
    val read: Long = 0,
    val write: Long = 0,
)

@Serializable
data class EnginePart(
    val id: String? = null,
    val type: String,
    val text: String? = null,
    val tool: String? = null,
)
