package com.garfiec.librechat.core.model.scheduler

import com.garfiec.librechat.core.model.engine.EngineModelRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The platform's roles and their models, as the scheduler's `GET /roles` gives them (08/11/2026,
 * D-086: roles, no more tiers). A role is an ordered list of model ids, the first IN SERVICE; the
 * table is the server's catalogue, rendered there, never the app's: which model thinks and which
 * one scouts is a deployment's choice, and a list hardcoded here would be wrong the day it changes.
 *
 * What the app does with it: it names who wrote what (the signatures, the spend) and says
 * « Piloté » for a conversation on the [pilot] (10/11/2026, Raphaël: one pilot, the éclaireur in
 * service, which consults the penseur, the navigateur and the savant through the engine's
 * sub-agents). What a mode means, its model and its rules, is the server's (10/11/2026: the app
 * sends no model in « Piloté » and the scheduler writes the rules); the picker offers the
 * [savant] for a conversation that is all heavy reasoning; « Ask the savant » under an answer
 * exists when the server has one. Empty when the scheduler predates the route.
 */
@Serializable
data class PlatformRoles(
    @SerialName("roles") val roles: Map<String, RoleModels> = emptyMap(),
    /** The role that leads every session (10/11/2026): the éclaireur. */
    @SerialName("pilote") val pilotRole: String = SCOUT,
    /** The provider a conversation spends on (D-077): `hobbitton-chat`. */
    @SerialName("fournisseur_conversations") val conversationsProvider: String = "hobbitton-chat",
) {
    /** The model id in service for [role], null when the role is unknown or empty. */
    fun inService(role: String): String? = roles[role]?.inService?.takeIf { it.isNotBlank() }

    /** The model a conversation runs on for [role]: its model in service, on the conversations' provider. */
    fun conversationModel(role: String): EngineModelRef? =
        inService(role)?.let { EngineModelRef(providerId = conversationsProvider, modelId = it) }

    /** The pilot in service, what « Piloté » runs on; null without a table. */
    val pilot: EngineModelRef? get() = conversationModel(pilotRole)

    /** The savant in service, the strongest model, for heavy reasoning; null without a table. */
    val savant: EngineModelRef? get() = conversationModel(SAVANT)

    /**
     * The role [modelId] belongs to, null when no list names it (08/11/2026): a model is in exactly
     * one role (the server's rule), so the role a message ran under follows from its model alone.
     */
    fun roleOf(modelId: String): String? =
        roles.entries.firstOrNull { (_, models) -> models.models.any { it.equals(modelId, ignoreCase = true) } }?.key

    /** The roles in the order the screen lists them: the pilot first, then the rest of the table. */
    val ordered: List<String> get() = (ORDER + roles.keys).distinct().filter { it in roles }

    /** True when [ref] is the model in service of [role], whatever the provider it is named on. */
    fun isInService(role: String, ref: EngineModelRef?): Boolean =
        ref != null && inService(role)?.equals(ref.modelId, ignoreCase = true) == true

    companion object {
        const val THINKER = "penseur"
        const val SCOUT = "eclaireur"
        const val SAVANT = "savant"

        const val NAVIGATOR = "navigateur"
        const val CODER = "codeur"
        const val CLERK = "commis"
        const val READER = "lecteur"

        /** The whole table in reading order: who leads, who is consulted, the rest. */
        val ORDER = listOf(SCOUT, THINKER, SAVANT, NAVIGATOR, CODER, CLERK, READER)
        val NONE = PlatformRoles()
    }
}

/** One role's models: the one in service and the whole ordered list (replacements, bench candidates). */
@Serializable
data class RoleModels(
    @SerialName("en_service") val inService: String? = null,
    @SerialName("modeles") val models: List<String> = emptyList(),
)

/** `provider/model` as the engine wants it, or null when the text names no provider. */
fun String.asModelRef(): EngineModelRef? {
    val provider = substringBeforeLast('/', "").trim()
    val model = substringAfterLast('/').trim()
    if (provider.isEmpty() || model.isEmpty()) return null
    return EngineModelRef(providerId = provider, modelId = model)
}
