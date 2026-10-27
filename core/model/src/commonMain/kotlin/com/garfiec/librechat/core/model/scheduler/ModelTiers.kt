package com.garfiec.librechat.core.model.scheduler

import com.garfiec.librechat.core.model.engine.EngineModelRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The tiers of model a conversation may run on, as the scheduler's `GET /palier` gives them
 * (25/10/2026, Raphaël's rule: the model necessary and sufficient, the cheapest): tier name to
 * `provider/model`, on the conversations' provider. Empty when the server set no tier models.
 *
 * The ladder is the server's, never the app's: which model is « stronger » than which is a
 * deployment's choice (today DeepSeek V4 Pro for `moyen`, Opus for `haut`), and a list hardcoded
 * here would be wrong the day it changes.
 */
@Serializable
data class ModelTiers(
    @SerialName("paliers") val models: Map<String, String> = emptyMap(),
    /** Whether the server has a trieur to read a first message; false means Auto picks the default. */
    @SerialName("triage") val triage: Boolean = false,
) {
    /** The tiers that have a model, lowest first. */
    val ladder: List<String> get() = TIER_ORDER.filter { it in models }

    /** The `provider/model` of the lowest tier, the cheapest the ladder offers; null when empty. */
    val lowest: String? get() = ladder.firstOrNull()?.let(models::getValue)

    /**
     * The tier above the one [modelId] belongs to, with its `provider/model`; null at the top of the
     * ladder, or for a model on no tier. Matched on the bare model id, whatever the provider: the
     * conversation runs its tier's model on the chat's provider, the ladder may name another.
     */
    fun strongerThan(modelId: String): Pair<String, String>? {
        val bare = modelId.substringAfterLast('/').lowercase()
        val tier = ladder.firstOrNull { models.getValue(it).substringAfterLast('/').lowercase() == bare } ?: return null
        val next = ladder.getOrNull(ladder.indexOf(tier) + 1) ?: return null
        return next to models.getValue(next)
    }

    companion object {
        /** The scheduler's tiers, lowest first (`paliers.PALIERS`). */
        val TIER_ORDER = listOf("bas", "moyen", "haut")
        val NONE = ModelTiers()
    }
}

/**
 * What the scheduler's trieur says of a first message (`POST /palier`): its tier and the model to
 * start on, both null when there is no trieur or it did not answer, and the ladder it chose from.
 */
@Serializable
data class ModelTierChoice(
    @SerialName("palier") val tier: String? = null,
    @SerialName("modele") val model: String? = null,
    @SerialName("paliers") val models: Map<String, String> = emptyMap(),
)

/** `provider/model` as the engine wants it, or null when the text names no provider. */
fun String.asModelRef(): EngineModelRef? {
    val provider = substringBeforeLast('/', "").trim()
    val model = substringAfterLast('/').trim()
    if (provider.isEmpty() || model.isEmpty()) return null
    return EngineModelRef(providerId = provider, modelId = model)
}
