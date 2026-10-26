package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EnginePartSnapshot
import com.garfiec.librechat.core.model.engine.EngineQuestionRequest
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
    /**
     * The questions the agent is waiting on, oldest first: its turn is blocked until each is
     * answered or dismissed. Filled by the live feed and by `GET /question` (the feed announces a
     * question once, and a screen opened after it would never hear of it otherwise).
     */
    val questions: List<EngineQuestionRequest> = emptyList(),
    /**
     * The session's context, in tokens: what its last model call read and wrote (24/10/2026, the
     * context indicator). Taken from the latest assistant message that reports any, since the
     * engine rewrites a message's tokens after each step; null until one does.
     */
    val contextTokens: Long? = null,
)

sealed interface ChatTurn {
    val key: String

    data class User(override val key: String, val parts: List<ChatPart> = emptyList()) : ChatTurn

    data class Assistant(
        override val key: String,
        val parts: List<ChatPart> = emptyList(),
        /** The engine's error on this turn, shown in place of an answer that never came. */
        val error: String? = null,
        /**
         * The turn holds a compaction's summary (25/10/2026): its parts are never kept and the
         * transcript does not draw it; the compaction's notice stands for it.
         */
        val summary: Boolean = false,
    ) : ChatTurn
}

sealed interface ChatPart {
    val id: String

    /** A block of prose. Rendered as markdown, like the chat's own bubbles. */
    data class Text(override val id: String, val text: String) : ChatPart

    /** The model's thinking, shown muted. */
    data class Reasoning(override val id: String, val text: String) : ChatPart

    /**
     * The engine compacted the conversation here (25/10/2026, as on Claude.ai): a one-line notice
     * in small print. [contextBefore] is the session's context when the compaction began,
     * [contextAfter] the size of the summary that replaces it; both null while unknown.
     */
    data class Compaction(
        override val id: String,
        val contextBefore: Long? = null,
        val contextAfter: Long? = null,
    ) : ChatPart {
        /** What the compaction took off the context, or null while it is not known. */
        val savedTokens: Long? get() = contextBefore?.let { before -> contextAfter?.let { before - it } }?.takeIf { it > 0 }
    }

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
        /** Files the tool handed back: shown after the fold, as content (19/10/2026). */
        val attachments: List<Attachment> = emptyList(),
    ) : ChatPart
}

/** One argument of a tool call: its name, and its value rendered as the compact JSON it arrived as. */
data class ToolArgument(val name: String, val value: String)

enum class ToolState { RUNNING, OK, FAILED }

/** Whether the screen has seen [partId] of [messageId] announced (a snapshot, live or replayed). */
fun MissionChatState.knowsPart(messageId: String, partId: String): Boolean =
    turns.any { turn ->
        turn.key == messageId && when (turn) {
            is ChatTurn.User -> turn.parts
            is ChatTurn.Assistant -> turn.parts
        }.any { it.id == partId }
    }

/**
 * Apply one event. Idempotent under replay: a message or part seen twice is updated in place rather
 * than duplicated, which is what lets the screen seed from history and then tail the feed without the
 * seam showing. A message not seen before takes its place in the ORDER of the ids (26/10/2026), not
 * at the end: the engine's ids grow with time, and the transcript is folded from three sources that
 * overlap (the tail the device kept, the engine's read, the live feed), so a message older than the
 * live turn used to land after it, its steps drawn under the ones that followed them.
 */
fun MissionChatState.reduce(event: EngineStreamEvent): MissionChatState = when (event) {
    is EngineStreamEvent.MessageStarted -> {
        val known = turns.any { it.key == event.messageId }
        when {
            // The model rides on the envelope, so it is read even for a message already seen — a
            // live turn re-announces itself as it fills in, and the first announcement can precede
            // the model being decided.
            known && event.summary -> copy(
                turns = turns.map { turn ->
                    if (turn is ChatTurn.Assistant && turn.key == event.messageId) turn.copy(summary = true, parts = emptyList()) else turn
                }.withSummarySize(event.summarySize()),
                contextTokens = event.summarySize() ?: contextTokens,
            )
            known -> copy(
                model = event.model ?: model,
                contextTokens = event.contextOr(contextTokens),
                turns = if (event.error == null) {
                    turns
                } else {
                    turns.map { turn ->
                        if (turn is ChatTurn.Assistant && turn.key == event.messageId) turn.copy(error = event.error) else turn
                    }
                },
            )
            event.role == ROLE_USER ->
                copy(turns = turns.withNewTurn(ChatTurn.User(event.messageId)))
            // Ne marque PAS le tour comme en cours. Un transcript rejoué émet un
            // `MessageStarted` par message d'assistant, sans `Idle` derrière : le faire
            // ici laissait le bouton Stop allumé en permanence sur une session terminée
            // depuis des heures. Constaté le 30/08/2026. Seul un delta prouve qu'un tour
            // parle maintenant — et seul le flux vivant en émet.
            event.summary ->
                copy(
                    turns = turns.withNewTurn(ChatTurn.Assistant(event.messageId, error = event.error, summary = true))
                        .withSummarySize(event.summarySize()),
                    contextTokens = event.summarySize() ?: contextTokens,
                )
            else ->
                copy(
                    turns = turns.withNewTurn(ChatTurn.Assistant(event.messageId, error = event.error)),
                    model = event.model ?: model,
                    contextTokens = event.contextOr(contextTokens),
                )
        }
    }

    is EngineStreamEvent.PartUpdated -> when {
        // A summary's text is not shown: the compaction's notice stands for it.
        turns.isSummary(event.messageId) -> this
        event.part.type == COMPACTION_PART -> copy(turns = turns.withCompaction(event.messageId, event.partId, contextTokens))
        else -> event.part.asChatPart(event.partId)?.let { copy(turns = turns.withPart(event.messageId, it)) } ?: this
    }

    is EngineStreamEvent.PartDelta ->
        if (event.field != FIELD_TEXT) {
            this
        } else if (turns.isSummary(event.messageId)) {
            // The summary is being written: the session works, but nothing of it is shown.
            copy(streaming = true)
        } else {
            copy(turns = turns.appendingText(event.messageId, event.partId, event.delta), streaming = true)
        }

    // An idle session has nothing waiting: a question still listed here was abandoned with its
    // turn (stopped, or failed) and the engine dropped it without a `question.rejected`.
    EngineStreamEvent.Idle -> copy(streaming = false, questions = emptyList())

    is EngineStreamEvent.QuestionAsked ->
        if (questions.any { it.id == event.request.id }) this else copy(questions = questions + event.request)

    is EngineStreamEvent.QuestionClosed -> copy(questions = questions.filterNot { it.id == event.requestId })
}

/** The engine's marker of a compaction, a part of the user message that asks for it. */
private const val COMPACTION_PART = "compaction"

private fun List<ChatTurn>.isSummary(messageId: String): Boolean =
    any { it is ChatTurn.Assistant && it.key == messageId && it.summary }

/**
 * The compaction notice, on the user message the engine opened for it. The context it starts from
 * is the session's at that moment, kept on later snapshots of the same part.
 */
private fun List<ChatTurn>.withCompaction(messageId: String, partId: String, context: Long?): List<ChatTurn> {
    val known = flatMap { turn -> if (turn is ChatTurn.User) turn.parts else emptyList() }
        .filterIsInstance<ChatPart.Compaction>().firstOrNull { it.id == partId }
    val part = known ?: ChatPart.Compaction(partId, contextBefore = context)
    val base = if (any { it.key == messageId }) this else withNewTurn(ChatTurn.User(messageId))
    return base.map { turn ->
        if (turn.key != messageId || turn !is ChatTurn.User) return@map turn
        turn.copy(parts = turn.parts.filterNot { it.id == partId } + part)
    }
}

/** The size of a written summary, the context the next turns start from; null while it is being written. */
private fun EngineStreamEvent.MessageStarted.summarySize(): Long? = tokens?.output?.takeIf { it > 0 }

/** Records [size] as what the latest compaction left, once the summary has reported it. */
private fun List<ChatTurn>.withSummarySize(size: Long?): List<ChatTurn> {
    if (size == null) return this
    val index = indexOfLast { turn -> turn is ChatTurn.User && turn.parts.any { it is ChatPart.Compaction } }
    if (index < 0) return this
    return mapIndexed { i, turn ->
        if (i != index || turn !is ChatTurn.User) return@mapIndexed turn
        turn.copy(parts = turn.parts.map { if (it is ChatPart.Compaction) it.copy(contextAfter = size) else it })
    }
}

/** An assistant envelope's context size when it reports one, [current] otherwise (a step still running reports zero). */
private fun EngineStreamEvent.MessageStarted.contextOr(current: Long?): Long? =
    tokens?.total?.takeIf { role != ROLE_USER && it > 0 } ?: current

/**
 * Pending questions read from the engine (`GET /question`), merged under what the feed said: a
 * question announced while the read was in flight is kept, a question the read lists is added.
 */
fun MissionChatState.withPendingQuestions(pending: List<EngineQuestionRequest>): MissionChatState =
    copy(questions = (questions + pending).distinctBy { it.id })

private const val ROLE_USER = "user"
private const val FIELD_TEXT = "text"
private const val DEFAULT_MIME = "application/octet-stream"

/**
 * The parts worth rendering. `step-start` and `step-finish` are the run's own bookkeeping — nobody
 * reads them — and a text part with nothing in it yet is not a blank bubble, it is a part waiting for
 * its deltas, so it is kept (empty) rather than dropped. A synthetic text is the model's alone (the
 * scheduler's ceiling notice, 19/10/2026): shown, it read as the person's own message.
 */
private fun EnginePartSnapshot.asChatPart(partId: String): ChatPart? = when (type) {
    "text" -> if (synthetic) null else ChatPart.Text(partId, text.orEmpty())
    // Dropped silently until 31/08/2026 — a message sent with a photo showed only its prose, and
    // nothing on screen said the photo had gone with it.
    "file" -> url?.let { ChatPart.Attachment(partId, mime ?: DEFAULT_MIME, it, filename) }
    "reasoning" -> ChatPart.Reasoning(partId, text.orEmpty())
    "tool" -> ChatPart.Tool(
        id = partId,
        name = input.directoryTarget(tool) ?: tool ?: callId ?: partId,
        arguments = (if (input.directoryTarget(tool) != null) input.directoryArguments() else input).asToolArguments(),
        output = output?.takeIf { it.isNotBlank() },
        attachments = attachments.mapIndexedNotNull { index, file ->
            file.url?.let { ChatPart.Attachment("$partId#$index", file.mime ?: DEFAULT_MIME, it, file.filename) }
        },
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
 * The tool a directory call really reaches. Since 08/2026 most tools are not called by name: the
 * engine calls `planificateur_annuaire_appeler` with `{"outil": "<real tool>", "arguments": {...}}`,
 * so every row read "planificateur_annuaire_appeler" and nobody could tell a bank read from an SSH
 * command. The row shows the real tool instead, and the call's inner arguments; null when [tool] is
 * not a directory call or names no tool.
 */
internal fun JsonElement?.directoryTarget(tool: String?): String? {
    if (tool == null || !tool.endsWith(DIRECTORY_CALL)) return null
    val target = ((this as? JsonObject)?.get("outil") as? JsonPrimitive)?.takeIf { it.isString }?.content
    return target?.takeIf { it.isNotBlank() }
}

/** The arguments the directory forwards to the real tool; absent or not an object reads as none. */
private fun JsonElement?.directoryArguments(): JsonElement? = (this as? JsonObject)?.get("arguments")

private const val DIRECTORY_CALL = "annuaire_appeler"

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

/**
 * A message the screen has not seen, at its place: the engine's ids grow with time, so the order of
 * the keys is the order of the conversation. Appended when it is the newest, the common case.
 */
private fun List<ChatTurn>.withNewTurn(turn: ChatTurn): List<ChatTurn> {
    val index = indexOfFirst { it.key > turn.key }
    return if (index < 0) this + turn else take(index) + turn + drop(index)
}

/** Upsert a part into its message, creating the message if the feed named it before announcing it. */
private fun List<ChatTurn>.withPart(messageId: String, part: ChatPart): List<ChatTurn> {
    val base = if (any { it.key == messageId }) this else withNewTurn(ChatTurn.Assistant(messageId))
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

/**
 * A delta on a part the screen knows grows it: prose in its bubble, reasoning inside its fold (so
 * a fold the reader opened follows the thinking).
 *
 * A delta on a part the screen was never told of (26/10/2026: the feed was joined after the part
 * began) is of unknown kind. The person's own words can only be prose; the assistant's are taken
 * for REASONING until a snapshot says otherwise, and the view model asks the transcript again,
 * which names the part within the second. Taken for prose, as before, a model's thinking streamed
 * as a full-width markdown answer and folded away when its snapshot came, several screens at a
 * time: the conversation jumped on every step. An answer taken for reasoning merely unfolds when
 * it is named.
 */
private fun List<ChatTurn>.appendingText(messageId: String, partId: String, delta: String): List<ChatTurn> {
    val base = if (any { it.key == messageId }) this else withNewTurn(ChatTurn.Assistant(messageId))
    return base.map { turn ->
        if (turn.key != messageId) return@map turn
        turn.mapParts { parts ->
            if (parts.none { it.id == partId }) {
                parts + if (turn is ChatTurn.User) ChatPart.Text(partId, delta) else ChatPart.Reasoning(partId, delta)
            } else {
                parts.map { part ->
                    when {
                        part.id != partId -> part
                        part is ChatPart.Text -> part.copy(text = part.text + delta)
                        part is ChatPart.Reasoning -> part.copy(text = part.text + delta)
                        else -> part
                    }
                }
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

/**
 * The transcript as it is drawn: every run of consecutive assistant turns made one.
 *
 * The engine writes one assistant message per step, and a mission that calls a tool, thinks, and
 * calls another wrote one message for each — so the screen stacked « 1 outil · réflexion », « 4
 * outils · réflexion », « 2 outils · réflexion »… one line per step, seven lines for one piece of
 * work (Raphaël, 02/10/2026). Drawn as one turn, those steps fall into [asBlocks]' rule: activity
 * with no prose between it is one block, whose count grows as the mission works.
 *
 * The run keeps its first turn's key, so the list item — and the fold the reader opened — stays put
 * while steps are appended to it.
 */
fun List<ChatTurn>.mergedAssistantRuns(): List<ChatTurn> {
    val merged = mutableListOf<ChatTurn>()
    forEach { turn ->
        val previous = merged.lastOrNull()
        if (turn is ChatTurn.Assistant && previous is ChatTurn.Assistant) {
            merged[merged.lastIndex] = previous.copy(parts = previous.parts + turn.parts, error = turn.error ?: previous.error)
        } else {
            merged += turn
        }
    }
    return merged
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
            is ChatPart.Text -> {
                // A text part that is still empty is a part awaiting its deltas, not a paragraph. It
                // would otherwise cut the activity run in two and open a second block mid-thought.
                val shown = part.text.withoutSanitisedPlaceholder()
                if (shown.isNotBlank()) {
                    flush()
                    blocks += ChatBlock.Prose(if (shown == part.text) part else part.copy(text = shown))
                }
            }
            is ChatPart.Attachment -> {
                flush()
                blocks += ChatBlock.Media(part)
            }
            // A page the tool rendered, a document it fetched: the agent says « here it is,
            // above », and it was folded away with the call (19/10/2026). Shown after it.
            is ChatPart.Tool -> {
                pending += part
                if (part.attachments.isNotEmpty()) {
                    flush()
                    part.attachments.forEach { blocks += ChatBlock.Media(it) }
                }
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
}.filterIsInstance<ChatPart.Text>().map { it.text.withoutSanitisedPlaceholder() }
    .filter { it.isNotBlank() }.joinToString("\n\n").trim()

/**
 * The text without the gateway's placeholders (20/10/2026). The gateway writes
 * « [System: Empty message content sanitised to satisfy protocol] » in place of an empty step the
 * engine hands it back, the model then reads its own earlier steps saying it and repeats it, and the
 * conversation filled with one such line between every two tool calls. It says nothing: dropped,
 * the tool calls around it fold into one block again. Its sibling « [System: Empty name
 * sanitised] » showed up on 21/10/2026: every « [System: … sanitised …] » goes the same way.
 */
internal fun String.withoutSanitisedPlaceholder(): String =
    if ("[System:" in this) replace(SANITISED_PLACEHOLDER, "").trim() else this

private val SANITISED_PLACEHOLDER = Regex("""\[System: [^\]\n]*saniti[sz]ed[^\]\n]*]""")
