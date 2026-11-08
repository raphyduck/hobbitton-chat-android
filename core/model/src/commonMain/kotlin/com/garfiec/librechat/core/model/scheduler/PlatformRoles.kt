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
 * What the app does with it: a conversation on « Piloté » runs on the [thinker] (the penseur in
 * service, which delegates the plumbing to the éclaireur through the engine's sub-agents); the
 * picker offers the [sage] for a conversation that is all heavy reasoning; « Ask the sage » under an
 * answer exists when the server has one. Empty when the scheduler predates the route.
 */
@Serializable
data class PlatformRoles(
    @SerialName("roles") val roles: Map<String, RoleModels> = emptyMap(),
    /** The roles a mission may be led by (`pilote`): penseur, eclaireur, sage. */
    @SerialName("pilotes") val pilots: List<String> = emptyList(),
    @SerialName("pilote_par_defaut") val defaultPilot: String = THINKER,
    /** The provider a conversation spends on (D-077): `hobbitton-chat`. */
    @SerialName("fournisseur_conversations") val conversationsProvider: String = "hobbitton-chat",
) {
    /** The model id in service for [role], null when the role is unknown or empty. */
    fun inService(role: String): String? = roles[role]?.inService?.takeIf { it.isNotBlank() }

    /** The model a conversation runs on for [role]: its model in service, on the conversations' provider. */
    fun conversationModel(role: String): EngineModelRef? =
        inService(role)?.let { EngineModelRef(providerId = conversationsProvider, modelId = it) }

    /** The penseur in service, what « Piloté » runs on; null without a table. */
    val thinker: EngineModelRef? get() = conversationModel(THINKER)

    /** The sage in service, the strongest model, for heavy reasoning; null without a table. */
    val sage: EngineModelRef? get() = conversationModel(SAGE)

    /** True when [ref] is the model in service of [role], whatever the provider it is named on. */
    fun isInService(role: String, ref: EngineModelRef?): Boolean =
        ref != null && inService(role)?.equals(ref.modelId, ignoreCase = true) == true

    companion object {
        const val THINKER = "penseur"
        const val SCOUT = "eclaireur"
        const val SAGE = "sage"
        /** The pilots a mission may name, in the order the screen shows them. */
        val PILOTS = listOf(THINKER, SCOUT, SAGE)
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
