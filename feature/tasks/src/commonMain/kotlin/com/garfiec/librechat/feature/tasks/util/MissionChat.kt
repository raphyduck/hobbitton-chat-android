package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineStreamEvent

/**
 * The conversation, folded from the engine's durable event stream.
 *
 * The stream is the single source of truth: it replays a session's whole history and then tails the
 * live reply, so one pure reducer over [EngineStreamEvent] reconstructs both. Text is folded the way
 * the wire delivers it — deltas append optimistically, and a `text.ended` overwrites with the
 * authoritative value — so a model that streams token by token and one that answers in a single block
 * both land on the same rendered text.
 *
 * Pure on purpose (the Tasks module has no Compose test harness): every rule here is pinned by
 * `MissionChatTest`, not discovered against a live engine.
 */
data class MissionChatState(
    val turns: List<ChatTurn> = emptyList(),
    /** An assistant answer is in flight — drives the spinner and the send/stop button's face. */
    val streaming: Boolean = false,
    /** A failure that belongs to no turn (the stream itself dropped for good). */
    val error: String? = null,
)

sealed interface ChatTurn {
    val key: String

    data class User(override val key: String, val text: String) : ChatTurn

    data class Assistant(
        override val key: String,
        val parts: List<ChatPart> = emptyList(),
        /** Set when the turn failed; the bubble shows it in place. */
        val failed: String? = null,
    ) : ChatTurn
}

sealed interface ChatPart {
    /** A block of the assistant's answer. `id` is the engine's `textID`, so deltas find their block. */
    data class Text(val id: String, val text: String) : ChatPart

    /** The model's thinking, shown muted. */
    data class Reasoning(val id: String, val text: String) : ChatPart

    /** A tool the assistant reached for, and how it ended. */
    data class Tool(val callId: String, val name: String, val state: ToolState) : ChatPart
}

enum class ToolState { RUNNING, OK, FAILED }

/** Fold a whole sequence — the replayed history, or a test's script — into one state. */
fun missionChatFrom(events: List<EngineStreamEvent>): MissionChatState =
    events.fold(MissionChatState()) { state, event -> state.reduce(event) }

/**
 * Apply one event. Every branch is total and idempotent enough to survive a replay: a re-seen
 * `prompt.admitted` does not add a second user bubble, and a tool announced twice (`input.started`
 * then `called`) stays one card.
 */
fun MissionChatState.reduce(event: EngineStreamEvent): MissionChatState = when (event) {
    is EngineStreamEvent.PromptAdmitted -> {
        val already = turns.any { it is ChatTurn.User && it.key == event.messageId }
        val next = if (already) turns else turns + ChatTurn.User(event.messageId, event.text.orEmpty())
        copy(turns = next, streaming = true, error = null)
    }

    is EngineStreamEvent.StepStarted ->
        copy(turns = ensureAssistant(event.assistantMessageId), streaming = true)

    is EngineStreamEvent.TextStarted ->
        copy(turns = withAssistant(event.assistantMessageId) { it.ensureText(event.textId) })

    is EngineStreamEvent.TextDelta ->
        copy(
            turns = withAssistant(event.assistantMessageId) { it.appendText(event.textId, event.delta) },
            streaming = true,
        )

    is EngineStreamEvent.TextEnded ->
        copy(turns = withAssistant(event.assistantMessageId) { it.setText(event.textId, event.text) })

    is EngineStreamEvent.ReasoningDelta ->
        copy(
            turns = withAssistant(event.assistantMessageId) { it.appendReasoning(event.reasoningId, event.delta) },
            streaming = true,
        )

    is EngineStreamEvent.ReasoningEnded -> {
        val text = event.text
        if (text == null) {
            this
        } else {
            copy(turns = withAssistant(event.assistantMessageId) { it.setReasoning(event.reasoningId, text) })
        }
    }

    is EngineStreamEvent.ToolStarted ->
        copy(
            turns = withAssistant(event.assistantMessageId) { it.ensureTool(event.callId, event.name) },
            streaming = true,
        )

    is EngineStreamEvent.ToolEnded ->
        copy(turns = withAssistant(event.assistantMessageId) { it.endTool(event.callId, event.ok) })

    is EngineStreamEvent.StepEnded ->
        // Only the canonical stop ends the turn; a step that ends to run a tool keeps the spinner up.
        copy(streaming = if (event.finish == FINISH_STOP) false else streaming)

    is EngineStreamEvent.Failed -> {
        val id = event.assistantMessageId
        if (id == null) {
            copy(streaming = false, error = event.error)
        } else {
            copy(turns = withAssistant(id) { it.copy(failed = event.error) }, streaming = false)
        }
    }
}

private const val FINISH_STOP = "stop"

private fun MissionChatState.ensureAssistant(id: String): List<ChatTurn> =
    if (turns.any { it is ChatTurn.Assistant && it.key == id }) {
        turns
    } else {
        turns + ChatTurn.Assistant(key = id)
    }

private inline fun MissionChatState.withAssistant(
    id: String,
    transform: (ChatTurn.Assistant) -> ChatTurn.Assistant,
): List<ChatTurn> {
    val present = turns.any { it is ChatTurn.Assistant && it.key == id }
    val base = if (present) turns else turns + ChatTurn.Assistant(key = id)
    return base.map { if (it is ChatTurn.Assistant && it.key == id) transform(it) else it }
}

private fun ChatTurn.Assistant.ensureText(textId: String): ChatTurn.Assistant =
    if (parts.any { it is ChatPart.Text && it.id == textId }) this else copy(parts = parts + ChatPart.Text(textId, ""))

private fun ChatTurn.Assistant.appendText(textId: String, delta: String): ChatTurn.Assistant =
    mapText(textId) { it.copy(text = it.text + delta) }

private fun ChatTurn.Assistant.setText(textId: String, text: String): ChatTurn.Assistant =
    mapText(textId) { it.copy(text = text) }

private inline fun ChatTurn.Assistant.mapText(
    textId: String,
    transform: (ChatPart.Text) -> ChatPart.Text,
): ChatTurn.Assistant {
    val present = parts.any { it is ChatPart.Text && it.id == textId }
    val base = if (present) parts else parts + ChatPart.Text(textId, "")
    return copy(parts = base.map { if (it is ChatPart.Text && it.id == textId) transform(it) else it })
}

private fun ChatTurn.Assistant.appendReasoning(reasoningId: String, delta: String): ChatTurn.Assistant =
    mapReasoning(reasoningId) { it.copy(text = it.text + delta) }

private fun ChatTurn.Assistant.setReasoning(reasoningId: String, text: String): ChatTurn.Assistant =
    mapReasoning(reasoningId) { it.copy(text = text) }

private inline fun ChatTurn.Assistant.mapReasoning(
    reasoningId: String,
    transform: (ChatPart.Reasoning) -> ChatPart.Reasoning,
): ChatTurn.Assistant {
    val present = parts.any { it is ChatPart.Reasoning && it.id == reasoningId }
    val base = if (present) parts else parts + ChatPart.Reasoning(reasoningId, "")
    return copy(parts = base.map { if (it is ChatPart.Reasoning && it.id == reasoningId) transform(it) else it })
}

private fun ChatTurn.Assistant.ensureTool(callId: String, name: String): ChatTurn.Assistant =
    if (parts.any { it is ChatPart.Tool && it.callId == callId }) {
        this
    } else {
        copy(parts = parts + ChatPart.Tool(callId, name, ToolState.RUNNING))
    }

private fun ChatTurn.Assistant.endTool(callId: String, ok: Boolean): ChatTurn.Assistant {
    val present = parts.any { it is ChatPart.Tool && it.callId == callId }
    val base = if (present) parts else parts + ChatPart.Tool(callId, callId, ToolState.RUNNING)
    val ended = if (ok) ToolState.OK else ToolState.FAILED
    return copy(parts = base.map { if (it is ChatPart.Tool && it.callId == callId) it.copy(state = ended) else it })
}
