package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EnginePartSnapshot
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
    /**
     * What the session's last assistant turn actually ran on.
     *
     * The engine takes a model per message and keeps it, so this — not the deployment's catalogue
     * default — is the answer to « which model is this session using ». The chip showed the default
     * until 30/08/2026, which named the right model only by coincidence.
     */
    val model: EngineModelRef? = null,
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

    /**
     * A file sent with a message — the attachment itself, bytes included.
     *
     * [dataUrl] is the part's own `url`: the engine has no upload route, so the file travels as a
     * `data:` URL and the transcript carries it whole. An image mime renders as the picture;
     * anything else renders as a named chip, because showing raw base64 helps nobody.
     */
    data class Attachment(
        override val id: String,
        val mime: String,
        val dataUrl: String,
        val filename: String?,
    ) : ChatPart

    /** A tool the assistant reached for, how it ended, and what passed through it. */
    data class Tool(
        override val id: String,
        val name: String,
        val state: ToolState,
        /** The call's arguments, flattened to one line each in the order the engine sent them. */
        val arguments: List<ToolArgument> = emptyList(),
        /** What the tool answered. Long — a median of 760 characters, a measured maximum of 51 425. */
        val output: String? = null,
    ) : ChatPart
}

/** One argument of a tool call: its name, and its value rendered as the compact JSON it arrived as. */
data class ToolArgument(val name: String, val value: String)

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
            // The model rides on the envelope, so it is read even for a message already seen — a
            // live turn re-announces itself as it fills in, and the first announcement can precede
            // the model being decided.
            known -> copy(model = event.model ?: model)
            event.role == ROLE_USER ->
                copy(turns = turns + ChatTurn.User(event.messageId))
            // Ne marque PAS le tour comme en cours. Un transcript rejoué émet un
            // `MessageStarted` par message d'assistant, sans `Idle` derrière : le faire
            // ici laissait le bouton Stop allumé en permanence sur une session terminée
            // depuis des heures. Constaté le 30/08/2026. Seul un delta prouve qu'un tour
            // parle maintenant — et seul le flux vivant en émet.
            else ->
                copy(turns = turns + ChatTurn.Assistant(event.messageId), model = event.model ?: model)
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
private const val DEFAULT_MIME = "application/octet-stream"

/**
 * The parts worth rendering. `step-start` and `step-finish` are the run's own bookkeeping — nobody
 * reads them — and a text part with nothing in it yet is not a blank bubble, it is a part waiting for
 * its deltas, so it is kept (empty) rather than dropped.
 */
private fun EnginePartSnapshot.asChatPart(partId: String): ChatPart? = when (type) {
    "text" -> ChatPart.Text(partId, text.orEmpty())
    // Dropped silently until 31/08/2026 — a message sent with a photo showed only its prose, and
    // nothing on screen said the photo had gone with it.
    "file" -> url?.let { ChatPart.Attachment(partId, mime ?: DEFAULT_MIME, it, filename) }
    "reasoning" -> ChatPart.Reasoning(partId, text.orEmpty())
    "tool" -> ChatPart.Tool(
        id = partId,
        name = tool ?: callId ?: partId,
        arguments = input.asToolArguments(),
        output = output?.takeIf { it.isNotBlank() },
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

/**
 * The argument object, flattened for display: one row per key, values rendered as the compact JSON
 * they arrived as. Nesting is not unfolded — an argument that is itself an object is shown as its
 * JSON, which is both honest and short enough to read on a phone.
 */
private fun JsonElement?.asToolArguments(): List<ToolArgument> {
    val obj = this as? JsonObject ?: return emptyList()
    return obj.map { (name, value) ->
        ToolArgument(
            name = name,
            // A string argument prints without its quotes; anything else prints as written.
            value = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: value.toString(),
        )
    }
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

/**
 * A turn's parts, cut into what one reads and what one merely checks.
 *
 * The chat draws the same line and for the same reason: an answer is prose, and the reasoning and
 * tool calls that produced it are the *process*. Left flat, a run of nine tool calls and a paragraph
 * of thinking buries the two sentences that were the point — which is what the tab shipped with on
 * 30/08/2026.
 *
 * Only **consecutive** activity groups. A tool call that comes back between two paragraphs belongs
 * to what follows it, not to the block above; merging across prose would reorder the turn.
 */
sealed interface ChatBlock {
    /** A block of prose, rendered as markdown at the message's own level. */
    data class Prose(val part: ChatPart.Text) : ChatBlock

    /** An attachment, shown in place — never folded into an activity group: it is content, not process. */
    data class Media(val part: ChatPart.Attachment) : ChatBlock

    /** Reasoning and tool calls, folded away by default. [key] is stable for remembering state. */
    data class Activity(val key: String, val parts: List<ChatPart>) : ChatBlock
}

/** Cut a turn's parts into readable blocks. Empty prose is dropped; empty activity never appears. */
fun List<ChatPart>.asBlocks(): List<ChatBlock> {
    val blocks = mutableListOf<ChatBlock>()
    val pending = mutableListOf<ChatPart>()

    fun flush() {
        if (pending.isNotEmpty()) {
            blocks += ChatBlock.Activity(key = pending.first().id, parts = pending.toList())
            pending.clear()
        }
    }

    forEach { part ->
        when (part) {
            is ChatPart.Text ->
                // A text part that is still empty is a part awaiting its deltas, not a paragraph. It
                // would otherwise cut the activity run in two and open a second block mid-thought.
                if (part.text.isNotBlank()) {
                    flush()
                    blocks += ChatBlock.Prose(part)
                }
            is ChatPart.Attachment -> {
                flush()
                blocks += ChatBlock.Media(part)
            }
            else -> pending += part
        }
    }
    flush()
    return blocks
}

/** What the folded header says a block contains: « 2 outils · réflexion ». */
fun ChatBlock.Activity.toolCount(): Int = parts.count { it is ChatPart.Tool }

fun ChatBlock.Activity.hasReasoning(): Boolean = parts.any { it is ChatPart.Reasoning }

/**
 * True while any tool in the block is still running — the one case the fold must not hide, because
 * a mission that is waiting on a tool looks identical to one that has stopped.
 */
fun ChatBlock.Activity.isRunning(): Boolean =
    parts.any { it is ChatPart.Tool && it.state == ToolState.RUNNING }

/** True when a tool in the block failed. A failure folded away is a failure nobody reads. */
fun ChatBlock.Activity.hasFailure(): Boolean =
    parts.any { it is ChatPart.Tool && it.state == ToolState.FAILED }

/** The visible text of a turn — what a bubble renders, and what an empty turn has none of. */
fun ChatTurn.text(): String = when (this) {
    is ChatTurn.User -> parts
    is ChatTurn.Assistant -> parts
}.filterIsInstance<ChatPart.Text>().joinToString("\n\n") { it.text }.trim()
