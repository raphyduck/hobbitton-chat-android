package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.scheduler.ModelPrice
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.model_price_per_million
import com.garfiec.librechat.core.ui.resources.model_price_unknown
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.round

/**
 * The price shown beside a model's name, wherever a model is picked.
 *
 * It lives here rather than in the chat or in the tasks tab because both pick models and both must
 * say the same thing about the same model. The two lists come from different services — LibreChat's
 * catalogue on one side, the engine's on the other — but they name the same gateway models, so a
 * price that read differently on the two screens would be a bug nobody could see from either one.
 *
 * **A null price is never a zero.** The gateway's price table ignores some models, and the
 * scheduler is careful to answer `null` rather than `0` for those; this renders that as words. A
 * « 0,00 $ » beside a model that charges is the worst possible reading — reassuring and false.
 */
@Composable
fun modelPriceLabel(price: ModelPrice?): String? = when {
    price == null -> null
    !price.isPriced -> stringResource(Res.string.model_price_unknown)
    else -> stringResource(
        Res.string.model_price_per_million,
        perMillion(price.input),
        perMillion(price.output),
    )
}

/**
 * A dollar amount per million tokens, short enough to sit inside a list row.
 *
 * Two decimals at most, trailing zeros dropped: « 5 », « 0.6 », « 1.25 ». The catalogue's cheapest
 * model still costs tenths of a dollar per million, so two decimals never round a real price to
 * zero — and a price that WOULD round to zero is shown as « <0.01 » rather than as « 0 », for the
 * same reason the missing prices are words and not numbers.
 */
internal fun perMillion(value: Double?): String {
    if (value == null) return UNKNOWN
    val cents = round(abs(value) * HUNDREDTHS).toLong()
    if (cents == 0L) return BELOW_ONE_CENT
    val whole = cents / HUNDREDTHS.toLong()
    val fraction = (cents % HUNDREDTHS.toLong()).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$whole" else "$whole.$fraction"
}

private const val HUNDREDTHS = 100.0
private const val UNKNOWN = "?"
private const val BELOW_ONE_CENT = "<0.01"
