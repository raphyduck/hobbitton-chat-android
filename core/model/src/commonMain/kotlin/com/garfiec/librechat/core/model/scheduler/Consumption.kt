package com.garfiec.librechat.core.model.scheduler

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the platform spent, by model and by day.
 *
 * Field names are the scheduler's, in French, for the same reason as [ScheduledMission]: the
 * server's `depenses.py` is the contract, and a rename either side does not follow would empty this
 * screen without anything failing anywhere.
 *
 * [totalSpend] deliberately does **not** include the models with no price. When [unpricedModels] is
 * non-empty the total is a *lower bound*, and the UI must say so — see [isComplete].
 */
@Serializable
data class Consumption(
    @SerialName("jours") val days: List<ConsumptionDay> = emptyList(),
    @SerialName("modeles") val models: List<ModelConsumption> = emptyList(),
    @SerialName("depense_totale") val totalSpend: Double = 0.0,
    @SerialName("jetons_totaux") val totalTokens: Long = 0,
    /**
     * Models the gateway counted tokens for but has no price for. **Not free** — see
     * [ModelConsumption.isPriced].
     */
    @SerialName("modeles_non_tarifes") val unpricedModels: List<String> = emptyList(),
    /**
     * What prefix caching avoided paying, over the period. Computed by the gateway rather than
     * estimated here: reproducing its price table is exactly what this feature refuses to do.
     */
    @SerialName("economie_du_cache") val cacheSavings: Double = 0.0,
) {
    /** False when at least one model has no price, i.e. [totalSpend] is a floor and not a total. */
    val isComplete: Boolean get() = unpricedModels.isEmpty()
}

@Serializable
data class ConsumptionDay(
    @SerialName("jour") val day: String,
    @SerialName("depense") val spend: Double = 0.0,
    @SerialName("jetons") val tokens: Long = 0,
    /** False when a model of that day had no price — the day's spend is then a lower bound. */
    @SerialName("complet") val isComplete: Boolean = true,
    @SerialName("modeles") val models: List<ModelConsumption> = emptyList(),
)

@Serializable
data class ModelConsumption(
    @SerialName("modele") val model: String,
    @SerialName("jetons") val tokens: Long = 0,
    @SerialName("jetons_entree") val inputTokens: Long = 0,
    @SerialName("jetons_sortie") val outputTokens: Long = 0,
    /**
     * Prompt tokens replayed from the provider's cache. **Contained in** [inputTokens], not added
     * to it — adding the two would count half the input twice, which is what the server's own test
     * pins down.
     */
    @SerialName("jetons_cache") val cachedTokens: Long = 0,
    /**
     * Null when the gateway has no price for this model — never `0.0`.
     *
     * The distinction is the whole point. The gateway writes a literal zero when its price table
     * does not know a model, and rendering that as « 0.00 $ » reads as « free » on precisely the
     * cheap models one routes traffic to in order to save money. A brand-new model can be missing
     * from that table for good — `kimi-k2.7-code` was, on 23/08/2026 — so this is a permanent
     * state, not a gap waiting to be filled.
     */
    @SerialName("depense") val spend: Double? = null,
    @SerialName("tarife") val isPriced: Boolean = true,
    @SerialName("appels") val calls: Int = 0,
    @SerialName("echecs") val failures: Int = 0,
)
