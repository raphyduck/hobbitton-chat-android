package com.garfiec.librechat.core.model.engine

import kotlinx.serialization.Serializable

/**
 * Session creation. `permission` is a list of rules (see [EnginePermissionRule]) and is what
 * enforces the brief's "connector selection per mission": the profile is a ceiling of capabilities,
 * the rule list narrows it for this mission only — never the other way round.
 *
 * **There is deliberately no `model` here, and adding one back breaks every mission that names a
 * model.** Since OpenCode 1.18.18 this route rejects the key outright — HTTP 400
 * `{"_tag":"BadRequest"}`, naming no field, so the failure reads like a malformed request rather
 * than an unsupported option. The server checked it form by form on 24/08/2026 (object, and
 * `"provider/model"` string): the permission list passes, `model` in any shape does not. It cost
 * three nights of a scheduled mission dying in 0,0 s before it existed. The model belongs on
 * [EnginePromptRequest], which is where it decides the call.
 */
@Serializable
data class CreateEngineSessionRequest(
    val agent: String,
    val title: String? = null,
    val permission: List<EnginePermissionRule>? = null,
)

/**
 * Sending the objective. The route is `prompt_async`, which returns immediately — that is what lets
 * a caller watch the ceilings *while* the mission runs instead of discovering them afterwards.
 * There is no `message/async`; asking for one returns 404.
 */
@Serializable
data class EnginePromptRequest(
    val parts: List<EnginePromptPart>,
    val agent: String? = null,
    val model: EngineModelRef? = null,
)

@Serializable
data class EnginePromptPart(
    val type: String = "text",
    val text: String,
)

/** Approve or refuse one pending permission request in interactive mode. */
@Serializable
data class EnginePermissionReply(
    val response: String,
) {
    companion object {
        const val ONCE = "once"
        const val ALWAYS = "always"
        const val REJECT = "reject"
    }
}
