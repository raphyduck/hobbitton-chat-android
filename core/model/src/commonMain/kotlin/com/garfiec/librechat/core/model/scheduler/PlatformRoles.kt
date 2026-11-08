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
 * picker offers the [savant] for a conversation that is all heavy reasoning; « Ask the savant » under an
 * answer exists when the server has one. Empty when the scheduler predates the route.
 */
@Serializable
data class PlatformRoles(
    @SerialName("roles") val roles: Map<String, RoleModels> = emptyMap(),
    /** The roles a mission may be led by (`pilote`): penseur, eclaireur, savant. */
    @SerialName("pilotes") val pilots: List<String> = emptyList(),
    @SerialName("pilote_par_defaut") val defaultPilot: String = THINKER,
    /** The provider a conversation spends on (D-077): `hobbitton-chat`. */
    @SerialName("fournisseur_conversations") val conversationsProvider: String = "hobbitton-chat",
    /**
     * What each role may reach (08/11/2026 evening, Raphaël: « le verrou se fait par rôle »): whether
     * it calls the annuaire itself and which sub-agents its session opens. The server's table; the
     * app writes a conversation's rules from the role of the model it sends. Empty when the
     * scheduler predates it: [accessOf] then falls back to the same table, hardcoded.
     */
    @SerialName("acces") val access: Map<String, RoleAccess> = emptyMap(),
) {
    /**
     * The access of [role]: the server's table, else the built-in one, which says the same thing
     * (the penseur and the savant delegate and never call the annuaire, the others call it and
     * never delegate). A null or unknown role is led as the penseur: it delegates.
     */
    fun accessOf(role: String?): RoleAccess =
        access[role ?: THINKER] ?: DEFAULT_ACCESS[role ?: THINKER] ?: DEFAULT_ACCESS.getValue(THINKER)

    /** The access of the role [ref] belongs to; a model no list names is led as the penseur. */
    fun accessOfModel(ref: EngineModelRef?): RoleAccess = accessOf(ref?.let { roleOf(it.modelId) })

    /** The model id in service for [role], null when the role is unknown or empty. */
    fun inService(role: String): String? = roles[role]?.inService?.takeIf { it.isNotBlank() }

    /** The model a conversation runs on for [role]: its model in service, on the conversations' provider. */
    fun conversationModel(role: String): EngineModelRef? =
        inService(role)?.let { EngineModelRef(providerId = conversationsProvider, modelId = it) }

    /** The penseur in service, what « Piloté » runs on; null without a table. */
    val thinker: EngineModelRef? get() = conversationModel(THINKER)

    /** The savant in service, the strongest model, for heavy reasoning; null without a table. */
    val savant: EngineModelRef? get() = conversationModel(SAVANT)

    /**
     * The role [modelId] belongs to, null when no list names it (08/11/2026): a model is in exactly
     * one role (the server's rule), so the role a message ran under follows from its model alone.
     */
    fun roleOf(modelId: String): String? =
        roles.entries.firstOrNull { (_, models) -> models.models.any { it.equals(modelId, ignoreCase = true) } }?.key

    /** The roles in the order the screen lists them: the pilots first, then the rest of the table. */
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

        /** The pilots a mission may name, in the order the screen shows them. */
        val PILOTS = listOf(THINKER, SCOUT, SAVANT)

        /** The whole table in reading order: who leads, who is consulted, who executes, the rest. */
        val ORDER = listOf(THINKER, SAVANT, SCOUT, NAVIGATOR, CODER, CLERK, READER)
        val NONE = PlatformRoles()

        /** The server's access table as of 08/11/2026, for a scheduler that does not publish it. */
        val DEFAULT_ACCESS: Map<String, RoleAccess> = mapOf(
            THINKER to RoleAccess(annuaire = false, subAgents = listOf(SCOUT, NAVIGATOR, SAVANT)),
            SAVANT to RoleAccess(annuaire = false, subAgents = listOf(SCOUT, NAVIGATOR)),
            SCOUT to RoleAccess(annuaire = true),
            NAVIGATOR to RoleAccess(annuaire = true, annuaireConnectors = listOf("bitwarden", "fichiers", "navigateur")),
            CODER to RoleAccess(annuaire = true),
            CLERK to RoleAccess(annuaire = false),
            READER to RoleAccess(annuaire = false),
        )
    }
}

/**
 * What a role reaches (08/11/2026 evening): [annuaire] says whether its session calls the annuaire
 * itself (false for a role that delegates, whose sub-agents call it), [annuaireConnectors] the
 * connectors it is bounded to when it does (null: its session's whole scope), [subAgents] the
 * sub-agents its session opens through `task`, by their conversation names.
 */
@Serializable
data class RoleAccess(
    @SerialName("annuaire") val annuaire: Boolean = true,
    @SerialName("connecteurs_annuaire") val annuaireConnectors: List<String>? = null,
    @SerialName("sous_agents") val subAgents: List<String> = emptyList(),
) {
    /** True when the role leads by delegating: its session opens sub-agents. */
    val delegates: Boolean get() = subAgents.isNotEmpty()
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
