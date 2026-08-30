package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EnginePartSnapshot
import com.garfiec.librechat.core.model.engine.EngineStreamEvent

/**
 * The conversation, folded from the engine's events.
 *
 * One reducer serves both the past and the present: a fetched transcript is replayed as the same
 * events a live turn emits (`engineHistoryEvents`), so opening a session and watching it answer go
 * through identical code and cannot drift apart.
 *
 * [MissionChatState.streaming] is driven by **deltas alone**, never by a message appearing: history
 * and a live turn deliver the same `MessageStarted`, and only one of them means « it is talking ».
 *
 * Parts are addressed by id, which is what makes the fold total: a snapshot
 * ([EngineStreamEvent.PartUpdated]) is authoritative and overwrites, a delta appends. The engine
 * sends an empty text part first, fills it with deltas, then closes it with a snapshot — so a
 * consumer honouring only snapshots still lands on the right text, just not live.
 *
 * Pure on purpose (this module has no Compose test harness): every rule is pinned by `MissionChatTest`.
 */
data class MissionChatState(
    val turns: List<ChatTurn> = emptyList(),
    /** A turn is running — drives the spinner and the send/stop button's face. */
    val streaming: Boolean = false,
    /** A failure that belongs to no turn (the feed itself gave up). */
    val error: String? = null,
)

sealed interface ChatTurn {
    val key: String

    data class User(override val key: String, val parts: List<ChatPart> = emptyList()) : ChatTurn

    data class Assistant(override val key: String, val parts: List<ChatPart> = emptyList()) : ChatTurn
}

sealed interface ChatPart {
    val id: String

    /** A block of prose. Rendered as markdown, like the chat's own bubbles. */
    data class Text(override val id: String, val text: String) : ChatPart

    /** The model's thinking, shown muted. */
    data class Reasoning(override val id: String, val text: String) : ChatPart

    /** A tool the assistant reached for, and how it ended. */
    data class Tool(
        override val id: String,
        val name: String,
        val state: ToolState,
    ) : ChatPart
}

enum class ToolState { RUNNING, OK, FAILED }

/** Fold a whole sequence — a replayed transcript, a live run, or a test's script — into one state. */
fun missionChatFrom(events: List<EngineStreamEvent>): MissionChatState =
    events.fold(MissionChatState()) { state, event -> state.reduce(event) }

/**
 * Apply one event. Idempotent under replay: a message or part seen twice is updated in place rather
 * than duplicated, which is what lets the screen seed from history and then tail the feed without the
 * seam showing.
 */
fun MissionChatState.reduce(event: EngineStreamEvent): MissionChatState = when (event) {
    is EngineStreamEvent.MessageStarted -> {
        val known = turns.any { it.key == event.messageId }
        when {
            known -> this
            event.role == ROLE_USER ->
                copy(turns = turns + ChatTurn.User(event.messageId), error = null)
            // Ne marque PAS le tour comme en cours. Un transcript rejoué émet un
            // `MessageStarted` par message d'assistant, sans `Idle` derrière : le faire
            // ici laissait le bouton Stop allumé en permanence sur une session terminée
            // depuis des heures. Constaté le 30/08/2026. Seul un delta prouve qu'un tour
            // parle maintenant — et seul le flux vivant en émet.
            else ->
                copy(turns = turns + ChatTurn.Assistant(event.messageId), error = null)
        }
    }

    is EngineStreamEvent.PartUpdated -> {
        val part = event.part.asChatPart(event.partId)
        if (part == null) this else copy(turns = turns.withPart(event.messageId, part))
    }

    is EngineStreamEvent.PartDelta ->
        if (event.field != FIELD_TEXT) {
            this
        } else {
            copy(turns = turns.appendingText(event.messageId, event.partId, event.delta), streaming = true)
        }

    EngineStreamEvent.Idle -> copy(streaming = false)
}

private const val ROLE_USER = "user"
private const val FIELD_TEXT = "text"

/**
 * The parts worth rendering. `step-start` and `step-finish` are the run's own bookkeeping — nobody
 * reads them — and a text part with nothing in it yet is not a blank bubble, it is a part waiting for
 * its deltas, so it is kept (empty) rather than dropped.
 */
private fun EnginePartSnapshot.asChatPart(partId: String): ChatPart? = when (type) {
    "text" -> ChatPart.Text(partId, text.orEmpty())
    "reasoning" -> ChatPart.Reasoning(partId, text.orEmpty())
    "tool" -> ChatPart.Tool(
        id = partId,
        name = tool ?: callId ?: partId,
        state = when (status) {
            "completed" -> ToolState.OK
            "error" -> ToolState.FAILED
            // No status yet means the call is still being assembled — not a success. Reading an
            // absent status as OK would put a ✓ on a tool that may still fail.
            else -> ToolState.RUNNING
        },
    )
    else -> null
}

/** Upsert a part into its message, creating the message if the feed named it before announcing it. */
private fun List<ChatTurn>.withPart(messageId: String, part: ChatPart): List<ChatTurn> {
    val base = if (any { it.key == messageId }) this else this + ChatTurn.Assistant(messageId)
    return base.map { turn ->
        if (turn.key != messageId) return@map turn
        turn.mapParts { parts ->
            if (parts.any { it.id == part.id }) {
                parts.map { if (it.id == part.id) part else it }
            } else {
                parts + part
            }
        }
    }
}

private fun List<ChatTurn>.appendingText(messageId: String, partId: String, delta: String): List<ChatTurn> {
    val base = if (any { it.key == messageId }) this else this + ChatTurn.Assistant(messageId)
    return base.map { turn ->
        if (turn.key != messageId) return@map turn
        turn.mapParts { parts ->
            if (parts.any { it.id == partId }) {
                parts.map { if (it is ChatPart.Text && it.id == partId) it.copy(text = it.text + delta) else it }
            } else {
                parts + ChatPart.Text(partId, delta)
            }
        }
    }
}

private inline fun ChatTurn.mapParts(transform: (List<ChatPart>) -> List<ChatPart>): ChatTurn = when (this) {
    is ChatTurn.User -> copy(parts = transform(parts))
    is ChatTurn.Assistant -> copy(parts = transform(parts))
}

/** The visible text of a turn — what a bubble renders, and what an empty turn has none of. */
fun ChatTurn.text(): String = when (this) {
    is ChatTurn.User -> parts
    is ChatTurn.Assistant -> parts
}.filterIsInstance<ChatPart.Text>().joinToString("\n\n") { it.text }.trim()
