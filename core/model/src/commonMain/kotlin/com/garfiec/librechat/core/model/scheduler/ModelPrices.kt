package com.garfiec.librechat.core.model.scheduler

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What each model costs, as the gateway itself knows it — in dollars per **million** tokens.
 *
 * Picking a model is picking a price, and until now no list said so: Opus and Haiku looked alike in
 * the picker and differed by a factor of twenty on the bill. The figure exists server-side — the
 * gateway carries the price table it values every call with — so it is fetched rather than copied.
 * A price hardcoded in the client drifts in silence: nothing fails the day a provider changes its
 * rate, the screen simply keeps lying.
 *
 * Unlike [ProviderHealth], obtaining this is free and instant: the scheduler reads a table the
 * gateway already holds in memory and calls no model, so a screen may ask for it as it opens.
 */
@Serializable
data class ModelPrices(
    @SerialName("modeles") val models: List<ModelPrice> = emptyList(),
    /**
     * The models the gateway has no price for, named. Kept alongside the list because « we don't
     * know » is an answer a screen may want to state once rather than repeat on every row.
     */
    @SerialName("modeles_non_tarifes") val unpriced: List<String> = emptyList(),
) {
    /**
     * Keyed by the model name a picker shows. Lower-cased because the chat's catalogue and the
     * gateway's own declaration have differed in case before, and a price that fails to match is
     * indistinguishable on screen from a price that does not exist.
     */
    val byModel: Map<String, ModelPrice> by lazy {
        models.associateBy { it.model.lowercase() }
    }

    companion object {
        /** What a screen shows before the first answer arrives, and after a failed one. */
        val NONE = ModelPrices()
    }
}

/**
 * One model's price, in dollars per million tokens.
 *
 * **Null is not zero.** LiteLLM's price table ignores some models — DeepSeek and Kimi were missing
 * from it on 23/08/2026, which is what puts zeroes in the spend report — and the scheduler renders
 * an unknown price as `null` precisely so a client cannot print it as a number. « 0,00 $ » next to
 * a model that charges is the worst of both worlds: reassuring and false. A row with no price says
 * so in words; it never shows a zero.
 */
@Serializable
data class ModelPrice(
    @SerialName("nom") val model: String,
    @SerialName("entree") val input: Double? = null,
    @SerialName("sortie") val output: Double? = null,
    @SerialName("cache_lecture") val cacheRead: Double? = null,
    @SerialName("cache_ecriture") val cacheWrite: Double? = null,
) {
    /**
     * True once an input or an output price is known — the two that let someone estimate a turn.
     * A model whose cache price alone was known would not, so it does not count as priced.
     */
    val isPriced: Boolean get() = input != null || output != null
}
