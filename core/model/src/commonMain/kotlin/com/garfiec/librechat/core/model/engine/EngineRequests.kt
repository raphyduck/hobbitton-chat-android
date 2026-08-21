package com.garfiec.librechat.core.model.engine

import kotlinx.serialization.Serializable

/**
 * Session creation. `permission` is a list of rules (see [EnginePermissionRule]) and is what
 * enforces the brief's "connector selection per mission": the profile is a ceiling of capabilities,
 * the rule list narrows it for this mission only — never the other way round.
 */
@Serializable
data class CreateEngineSessionRequest(
    val agent: String,
    val title: String? = null,
    val permission: List<EnginePermissionRule>? = null,
    val model: EngineModelRef? = null,
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
