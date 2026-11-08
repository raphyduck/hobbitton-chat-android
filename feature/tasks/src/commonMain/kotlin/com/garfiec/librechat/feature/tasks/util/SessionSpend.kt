package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineCacheTokens
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.model.engine.EngineTokens
import com.garfiec.librechat.core.model.scheduler.ModelPrice
import com.garfiec.librechat.core.model.scheduler.ModelPrices
import com.garfiec.librechat.core.model.scheduler.PlatformRoles

/**
 * Who wrote a message, as the transcript signs it (08/11/2026, Raphaël: « savoir chaque outil ou
 * message ou raisonnement a été appelé par quel modèle, entre parenthèses, quel rôle, de manière
 * discrète »): the role the model belongs to, null when the roles table does not name it, and the
 * model id. The screen puts the words on it.
 */
data class ModelSignature(val role: String?, val modelId: String)

fun signatureOf(ref: EngineModelRef?, roles: PlatformRoles): ModelSignature? =
    ref?.let { ModelSignature(role = roles.roleOf(it.modelId), modelId = it.modelId) }

/** One line of the spend sheet: a model under its role, over the parent session and its children. */
data class SpendLine(
    val role: String?,
    val modelId: String,
    /** Model calls: the assistant messages the engine wrote on this model. */
    val calls: Int,
    /** Tool calls those messages made. */
    val tools: Int,
    val tokens: EngineTokens,
    /** Dollars, null when neither the engine nor the price table could say. */
    val cost: Double?,
    /** True when [cost] is the app's estimate from the tokens and the gateway's prices, not the engine's figure. */
    val estimated: Boolean,
)

/**
 * The session's spend, by role then model (08/11/2026): what the second small button of the top
 * bar opens. [children] counts the sub-agents' sessions folded in; [estimated] says the total
 * holds at least one line priced by the app rather than by the engine.
 */
data class SessionSpend(
    val lines: List<SpendLine>,
    val children: Int,
) {
    val total: Double? get() = lines.mapNotNull { it.cost }.takeIf { it.isNotEmpty() }?.sum()
    val estimated: Boolean get() = lines.any { it.estimated && it.cost != null }
    val calls: Int get() = lines.sumOf { it.calls }
    val tools: Int get() = lines.sumOf { it.tools }
}

/**
 * Folds the replayed transcripts of a session and of its children into [SessionSpend].
 *
 * One assistant message is one model call, charged by the engine ([EngineStreamEvent.MessageStarted.cost])
 * when its model carries a tariff; a zero or missing figure falls back to the tokens priced at the
 * gateway's table ([ModelPrices]), marked estimated, and stays null when the table does not know
 * the model either. Tool calls are the message's tool parts. Lines come out in the roles' reading
 * order ([PlatformRoles.ordered]), unknown roles last, the dearest model first within a role.
 */
fun sessionSpend(
    parent: List<EngineStreamEvent>,
    children: List<List<EngineStreamEvent>>,
    roles: PlatformRoles,
    prices: ModelPrices,
): SessionSpend {
    val calls = (listOf(parent) + children).flatMap { it.modelCalls() }
    val lines = calls
        .groupBy { it.model.modelId.lowercase() }
        .map { (_, group) ->
            val modelId = group.first().model.modelId
            val price = prices.byModel[modelId.lowercase()]
            val priced = group.map { call -> call.cost(price) }
            SpendLine(
                role = roles.roleOf(modelId),
                modelId = modelId,
                calls = group.size,
                tools = group.sumOf { it.tools },
                tokens = group.fold(EngineTokens()) { acc, call -> acc + call.tokens },
                cost = priced.mapNotNull { it.first }.takeIf { it.isNotEmpty() }?.sum(),
                estimated = priced.any { it.second },
            )
        }
    val order = roles.ordered
    val sorted = lines.sortedWith(
        compareBy<SpendLine> { line -> line.role?.let { order.indexOf(it).takeIf { i -> i >= 0 } } ?: order.size }
            .thenByDescending { it.cost ?: -1.0 }
            .thenByDescending { it.calls },
    )
    return SessionSpend(lines = sorted, children = children.size)
}

private class ModelCall(val model: EngineModelRef, val tokens: EngineTokens, val engineCost: Double?, val tools: Int) {
    /** The engine's figure when it has one, else the estimate: (dollars or null, estimated). */
    fun cost(price: ModelPrice?): Pair<Double?, Boolean> {
        val charged = engineCost?.takeIf { it > 0.0 }
        if (charged != null) return charged to false
        val estimate = price?.estimate(tokens) ?: return null to true
        return estimate to true
    }
}

private fun List<EngineStreamEvent>.modelCalls(): List<ModelCall> {
    val tools = mutableMapOf<String, MutableSet<String>>()
    val started = linkedMapOf<String, EngineStreamEvent.MessageStarted>()
    forEach { event ->
        when (event) {
            is EngineStreamEvent.MessageStarted ->
                if (event.role == ASSISTANT && event.model != null) started[event.messageId] = event
            is EngineStreamEvent.PartUpdated ->
                if (event.part.type == TOOL_PART) tools.getOrPut(event.messageId) { mutableSetOf() } += event.partId
            else -> Unit
        }
    }
    return started.values.map { event ->
        ModelCall(
            model = event.model!!,
            tokens = event.tokens ?: EngineTokens(),
            engineCost = event.cost,
            tools = tools[event.messageId]?.size ?: 0,
        )
    }
}

/** Dollars for [tokens] at this price, per million; null when the table has no input or output price. */
fun ModelPrice.estimate(tokens: EngineTokens): Double? {
    val input = input ?: return null
    val output = output ?: return null
    val cacheRead = tokens.cache?.read ?: 0
    val cacheWrite = tokens.cache?.write ?: 0
    return (
        tokens.input * input +
            (tokens.output + tokens.reasoning) * output +
            cacheRead * (this.cacheRead ?: input) +
            cacheWrite * (this.cacheWrite ?: input)
        ) / MILLION_TOKENS
}

private operator fun EngineTokens.plus(other: EngineTokens): EngineTokens =
    EngineTokens(
        input = input + other.input,
        output = output + other.output,
        reasoning = reasoning + other.reasoning,
        cache = EngineCacheTokens(
            read = (cache?.read ?: 0) + (other.cache?.read ?: 0),
            write = (cache?.write ?: 0) + (other.cache?.write ?: 0),
        ),
    )

/**
 * A dollar amount as the sheet prints it: cents, a comma, « < 0,01 $ » under a cent, « 12,40 $ »
 * past it. The app's own French formatting, like [compactTokens].
 */
fun formatDollars(amount: Double): String {
    if (amount < 0.005) return "< 0,01 $"
    val cents = (amount * 100 + 0.5).toLong()
    val whole = cents / 100
    val rest = cents % 100
    return "$whole,${rest.toString().padStart(2, '0')} $"
}

private const val ASSISTANT = "assistant"
private const val TOOL_PART = "tool"
private const val MILLION_TOKENS = 1_000_000.0
