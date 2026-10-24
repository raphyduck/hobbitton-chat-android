package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.scheduler.ModelPrices

/**
 * How full the session's context is (24/10/2026, Raphaël: the ring Claude's app shows in a
 * session's top bar). [used] comes from the engine (the last model call's tokens,
 * [MissionChatState.contextTokens]); [window] from the gateway, through the scheduler's price table
 * (`contexte`, its `max_input_tokens`), for the model the next message goes to: that is the model
 * the context must fit. Null window: the gateway does not know it, and the sheet gives the count
 * alone rather than a made-up percentage.
 */
data class ContextUsage(val used: Long, val window: Long?) {
    /** From 0 to 1, capped: a session past its window reads full, not 130 %. Null without a window. */
    val fraction: Float? get() = window?.let { (used.toDouble() / it).coerceIn(0.0, 1.0).toFloat() }

    /** The percentage shown, rounded down so « 100 % » means really full. */
    val percent: Int? get() = window?.let { ((used * PERCENT) / it).coerceIn(0, PERCENT.toLong()).toInt() }

    /** Past [NEARLY_FULL] of the window: the ring and the bar take the error colour. */
    val nearlyFull: Boolean get() = (fraction ?: 0f) >= NEARLY_FULL
}

/** The session's usage, or null before the first answer has reported its tokens. */
fun contextUsage(tokens: Long?, model: EngineSelectableModel?, prices: ModelPrices): ContextUsage? {
    if (tokens == null || tokens <= 0) return null
    val window = model?.let { prices.byModel[it.modelId.lowercase()]?.contextWindow }?.takeIf { it > 0 }
    return ContextUsage(used = tokens, window = window)
}

/**
 * A token count as the sheet writes it: « 950 », « 12,4 k », « 290,8 k », « 1 M », « 1,2 M ».
 * French decimals (a comma), one decimal at most, none when it is zero.
 */
fun compactTokens(count: Long): String = when {
    count >= MILLION -> "${oneDecimal(count.toDouble() / MILLION)} M"
    count >= THOUSAND -> "${oneDecimal(count.toDouble() / THOUSAND)} k"
    else -> count.toString()
}

private fun oneDecimal(value: Double): String {
    val tenths = (value * 10).toLong()
    val whole = tenths / 10
    val decimal = tenths % 10
    return if (decimal == 0L) whole.toString() else "$whole,$decimal"
}

private const val PERCENT = 100
private const val NEARLY_FULL = 0.8f
private const val THOUSAND = 1_000L
private const val MILLION = 1_000_000L
