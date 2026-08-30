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
                        ),
                    ),
                )
            }
        }
    }
