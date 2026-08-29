package com.garfiec.librechat.core.model.engine

/**
 * One thing that happened inside a session, as the engine's durable event stream reports it
 * (`GET /api/session/{id}/event`). That stream replays a session's whole history and then tails what
 * happens next, so the same handful of types carry both the past and the live reply — the wire
 * contract was captured against the running engine on 29/08/2026 (server PROGRESS log, "contrat
 * d'événements").
 *
 * Only the shapes the chat renders are modelled here; every other durable type parses to `null` and
 * merely advances the resume cursor. Assistant text arrives one of two ways, depending on the model:
 * a run of [TextDelta] closed by a [TextEnded], or a single [TextEnded] that carries the whole thing
 * at once (some providers buffer and never emit a delta). A consumer therefore treats
 * [TextEnded.text] as the authoritative value and the deltas as an optimistic preview of it.
 */
sealed interface EngineStreamEvent {

    /** The engine accepted the user's message and gave it an id. Marks the start of a turn. */
    data class PromptAdmitted(val messageId: String, val text: String?) : EngineStreamEvent

    /** A new assistant answer began, on this model. Its `assistantMessageID` keys everything below. */
    data class StepStarted(
        val assistantMessageId: String,
        val agent: String?,
        val modelId: String?,
    ) : EngineStreamEvent

    data class TextStarted(val assistantMessageId: String, val textId: String) : EngineStreamEvent

    data class TextDelta(
        val assistantMessageId: String,
        val textId: String,
        val delta: String,
    ) : EngineStreamEvent

    /** The whole text of this block, authoritative — reconcile any accumulated deltas to it. */
    data class TextEnded(
        val assistantMessageId: String,
        val textId: String,
        val text: String,
    ) : EngineStreamEvent

    data class ReasoningDelta(
        val assistantMessageId: String,
        val reasoningId: String,
        val delta: String,
    ) : EngineStreamEvent

    data class ReasoningEnded(
        val assistantMessageId: String,
        val reasoningId: String,
        val text: String?,
    ) : EngineStreamEvent

    /** The assistant reached for a tool. `name` is the tool's name; `callId` pairs it with its end. */
    data class ToolStarted(
        val assistantMessageId: String,
        val callId: String,
        val name: String,
    ) : EngineStreamEvent

    data class ToolEnded(
        val assistantMessageId: String,
        val callId: String,
        val ok: Boolean,
        val error: String?,
    ) : EngineStreamEvent

    /** The assistant's answer finished. `finish` is the engine's stop reason ("stop", …). */
    data class StepEnded(val assistantMessageId: String, val finish: String?) : EngineStreamEvent

    /** The turn failed. `assistantMessageId` is null when the failure predates any step. */
    data class Failed(val assistantMessageId: String?, val error: String) : EngineStreamEvent
}
