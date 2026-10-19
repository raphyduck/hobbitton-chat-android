package com.garfiec.librechat.core.model.engine

import kotlinx.serialization.json.JsonElement

/**
 * One thing that happened inside a session, as the engine's **classic** event feed reports it
 * (`GET /event`, global, filtered by session).
 *
 * **The engine has two parallel worlds and they do not talk to each other.** Missions are launched
 * through the classic routes (`prompt_async`), and a session that has run there is invisible to the
 * v2 surface: its durable feed (`/api/session/{id}/event`) never even answers, and a v2 prompt on it
 * is admitted and then never executed — no step ever starts. Measured against the live engine on
 * 29/08/2026, on a session created exactly the way the app creates one. That is why this model is
 * built on the classic shapes: they are the ones a mission actually speaks.
 *
 * History and live streaming share these events on purpose — `engineHistoryEvents` replays a fetched
 * transcript as the same sequence a live turn produces, so one reducer serves both and the seam
 * between « what already happened » and « what is happening » cannot drift.
 */
sealed interface EngineStreamEvent {

    /**
     * A message began, or its envelope changed. `role` is "user" or "assistant".
     *
     * [model] is what this turn ran on, when the engine says so — it does on every assistant message,
     * in history and on the live feed alike. It is the only honest source for « which model is this
     * session using »: the model travels per message, so the session's answer to that question is
     * whatever its last turn actually used, not what the deployment's catalogue defaults to.
     */
    data class MessageStarted(
        val messageId: String,
        val role: String,
        val model: EngineModelRef? = null,
        /**
         * Why this turn produced nothing, when the engine filed an error on it (11/10/2026). A
         * refused model call does not end in a part: the engine writes the error on the message's
         * envelope and the session falls idle. Without this the conversation stayed blank under
         * the person's question (« Can't see replies », a gateway budget refusal).
         */
        val error: String? = null,
    ) : EngineStreamEvent

    /**
     * A part of a message, as a whole snapshot — the authoritative value. Text parts arrive first
     * empty and are then filled by [PartDelta]s, but every delta run is closed by a snapshot, so a
     * consumer that only honours snapshots still ends up correct, just not live.
     */
    data class PartUpdated(
        val messageId: String,
        val partId: String,
        val part: EnginePartSnapshot,
    ) : EngineStreamEvent

    /**
     * A token-level append to one part. `field` names what grows — only "text" is rendered; the
     * engine also deltas tool input, which the chat shows as a card rather than as characters.
     */
    data class PartDelta(
        val messageId: String,
        val partId: String,
        val field: String,
        val delta: String,
    ) : EngineStreamEvent

    /** The session went idle: nothing is running any more. This is what ends the spinner. */
    data object Idle : EngineStreamEvent

    /**
     * The agent asked the person something (`question.asked`) and its turn is now waiting on the
     * answer. Live feed only: the transcript does not carry pending requests, `GET /question` does.
     */
    data class QuestionAsked(val request: EngineQuestionRequest) : EngineStreamEvent

    /**
     * A question stopped waiting, answered (`question.replied`) or dismissed (`question.rejected`),
     * from this phone or from anywhere else. Either way its form has nothing left to do.
     */
    data class QuestionClosed(val requestId: String) : EngineStreamEvent
}

/** The shape of a part, whether it came from the live feed or from a fetched transcript. */
data class EnginePartSnapshot(
    val type: String,
    val text: String? = null,
    val tool: String? = null,
    val callId: String? = null,
    /** "completed", "error", "running"… — absent while the call is still being assembled. */
    val status: String? = null,
    /**
     * What the tool was called with, and what it answered. Both are on the wire and both were
     * dropped until 30/08/2026 — the fold showed a tool's name and a tick, which says that
     * something happened and nothing about what.
     */
    val input: JsonElement? = null,
    val output: String? = null,
    /** Files a tool handed back with its answer (see [EngineToolState.attachments]). */
    val attachments: List<EngineFileAttachment> = emptyList(),
    /** A `type = "file"` part — an attachment sent with a message, its bytes as a data URL. */
    val mime: String? = null,
    val url: String? = null,
    val filename: String? = null,
)

/**
 * A fetched transcript, replayed as the events a live turn would have produced.
 *
 * Lets the screen seed itself from `GET /session/{id}/message` — the only place a mission's past
 * actually lives — without a second rendering path beside the streaming one.
 */
fun engineHistoryEvents(messages: List<EngineMessage>): List<EngineStreamEvent> =
    messages.flatMap { message ->
        val id = message.info.id
        buildList {
            add(
                EngineStreamEvent.MessageStarted(
                    messageId = id,
                    role = message.info.role,
                    model = message.info.modelId?.let { modelId ->
                        message.info.providerId?.let { EngineModelRef(it, modelId) }
                    },
                    error = message.info.error?.readable(),
                ),
            )
            message.parts.forEachIndexed { index, part ->
                add(
                    EngineStreamEvent.PartUpdated(
                        messageId = id,
                        // A stored part carries its own id; the index is the fallback so two
                        // unidentified parts of one message stay two parts rather than collapsing.
                        partId = part.id ?: "$id#$index",
                        part = EnginePartSnapshot(
                            type = part.type,
                            text = part.text,
                            tool = part.tool,
                            callId = part.callId,
                            status = part.state?.status,
                            input = part.state?.input,
                            output = part.state?.output,
                            attachments = part.state?.attachments.orEmpty(),
                            mime = part.mime,
                            url = part.url,
                            filename = part.filename,
                        ),
                    ),
                )
            }
        }
    }

/**
 * An engine error as one line a person can read: the provider's own message when there is one,
 * unwrapped from the JSON the gateway nests it in (`{"error":{"message":"ExceededBudget: …"}}`),
 * else the error's name.
 */
fun EngineError.readable(): String = readableEngineError(data?.message, name)

/** Shared by the transcript and the live feed, which read the same two fields off different shapes. */
fun readableEngineError(message: String?, name: String?): String {
    val text = message?.let(::innermostMessage)?.takeIf { it.isNotBlank() }
    return text ?: name?.takeIf { it.isNotBlank() } ?: "error"
}

/** The deepest `"message":"…"` in a string that may carry JSON inside JSON, escapes undone. */
private fun innermostMessage(raw: String): String {
    var current = raw.trim()
    repeat(MAX_NESTING) {
        val match = MESSAGE_FIELD.find(current) ?: return current
        current = match.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
    }
    return current
}

private val MESSAGE_FIELD = Regex(""""message"\s*:\s*"((?:[^"\\]|\\.)*)"""")
private const val MAX_NESTING = 4
