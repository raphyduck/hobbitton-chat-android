package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.scheduler.ModelConsumption

/**
 * The per-model breakdown, ordered by what each model actually **cost**.
 *
 * The scheduler serves it by volume, which answers a different question: `kimi-k3` burned twice the
 * tokens of `claude-sonnet-5` over the same week and cost a dollar less. A spend panel is read to
 * find where the money went, so the money is what orders it (demandé le 31/08/2026).
 *
 * A model with no price sorts **last**, not first and not as zero. Its cost is unknown, and an
 * unknown belongs at the bottom of a ranking of amounts rather than pretending to a rank it cannot
 * hold — the same reason the amount itself reads « no price » instead of « 0.00 $ ». Between two
 * unpriced models, volume is the only thing left to order them by.
 */
fun List<ModelConsumption>.byCostDescending(): List<ModelConsumption> =
    sortedWith(
        compareByDescending<ModelConsumption> { it.spend != null }
            .thenByDescending { it.spend ?: 0.0 }
            .thenByDescending { it.tokens },
    )

/**
 * What a million tokens of this model actually cost, over the period — or null when that cannot be
 * said.
 *
 * **Observed, not quoted.** This is the week's spend divided by the week's tokens: it blends input,
 * output and cache-replayed prompt at whatever ratio the traffic happened to have, so it is the
 * price *paid*, not a line from a tariff sheet. That is the number worth showing next to a total,
 * because it is the one that explains the total — but it moves with the traffic, and two weeks of
 * the same model will not give the same figure.
 *
 * Null on an unpriced model (its spend is unknown, not zero) and on a model with no tokens (nothing
 * to divide by). Null on a **partial** one too: half a price over all the tokens understates the
 * rate, and a rate that quietly reads low is worse than no rate at all.
 */
fun ModelConsumption.observedPricePerMillion(): Double? {
    val spend = spend ?: return null
    if (isPartial || tokens <= 0) return null
    return spend * TOKENS_PER_MILLION / tokens
}

private const val TOKENS_PER_MILLION = 1_000_000
