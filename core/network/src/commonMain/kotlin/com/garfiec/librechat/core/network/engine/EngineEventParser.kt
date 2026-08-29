package com.garfiec.librechat.core.network.engine

import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.network.sse.SseEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Turns one SSE frame off the engine's durable feed into a domain [EngineStreamEvent].
 *
 * The engine frames every event the plain way — a single `data: {json}` line per event, no `event:`
 * line — so [com.garfiec.librechat.core.network.sse.SseLineParser] already hands us the JSON whole in
 * [SseEvent.data]. The event's *kind* lives inside that JSON under `type`; its ordering lives under
 * `durable.seq`. This class reads both and maps the shapes the chat cares about, leaving every other
 * durable type to advance the cursor without producing an event.
 *
 * Pure and platform-free on purpose: the whole point of Stage 3 is that the mapping is unit-tested
 * against captured frames rather than a live engine.
 */
class EngineEventParser(private val json: Json) {

    /**
     * @param seq the frame's `durable.seq`, so a caller can resume the stream after it (`?after=`).
     *   Present even when [event] is null, which is how an unmodelled type still moves the cursor.
     * @param event the mapped event, or null for a frame that is not one the chat renders.
     */
    data class Parsed(val seq: Long?, val event: EngineStreamEvent?)

    /** Null when [frame] is not a JSON object with a `type` — a stray comment or a malformed line. */
    fun parse(frame: SseEvent): Parsed? {
        if (frame.data.isBlank()) return null
        val root = runCatching { json.parseToJsonElement(frame.data) }.getOrNull() as? JsonObject ?: return null
        val type = root.str("type") ?: return null
        val seq = (root["durable"] as? JsonObject)?.get("seq")?.let { (it as? JsonPrimitive)?.longOrNull }
        val data = root["data"] as? JsonObject
        return Parsed(seq, data?.let { map(type, it) })
    }

    @Suppress("CyclomaticComplexMethod")
    private fun map(type: String, data: JsonObject): EngineStreamEvent? = when (type) {
        "session.next.prompt.admitted" ->
            data.str("messageID")?.let { id ->
                EngineStreamEvent.PromptAdmitted(messageId = id, text = (data["prompt"] as? JsonObject)?.str("text"))
            }

        "session.next.step.started" ->
            data.str("assistantMessageID")?.let { id ->
                EngineStreamEvent.StepStarted(
                    assistantMessageId = id,
                    agent = data.str("agent"),
                    modelId = (data["model"] as? JsonObject)?.str("id"),
                )
            }

        "session.next.text.started" ->
            data.str("assistantMessageID")?.let { id ->
                EngineStreamEvent.TextStarted(assistantMessageId = id, textId = data.str("textID") ?: "")
            }

        "session.next.text.delta" ->
            data.str("assistantMessageID")?.let { id ->
                EngineStreamEvent.TextDelta(
                    assistantMessageId = id,
                    textId = data.str("textID") ?: "",
                    delta = data.str("delta") ?: "",
                )
            }

        "session.next.text.ended" ->
            data.str("assistantMessageID")?.let { id ->
                EngineStreamEvent.TextEnded(
                    assistantMessageId = id,
                    textId = data.str("textID") ?: "",
                    text = data.str("text") ?: "",
                )
            }

        "session.next.reasoning.delta" ->
            data.str("assistantMessageID")?.let { id ->
                EngineStreamEvent.ReasoningDelta(
                    assistantMessageId = id,
                    reasoningId = data.str("reasoningID") ?: "",
                    delta = data.str("delta") ?: "",
                )
            }

        "session.next.reasoning.ended" ->
            data.str("assistantMessageID")?.let { id ->
                EngineStreamEvent.ReasoningEnded(
                    assistantMessageId = id,
                    reasoningId = data.str("reasoningID") ?: "",
                    text = data.str("text"),
                )
            }

        // Two events can announce a tool: `tool.input.started` names it early (`name`), `tool.called`
        // names it with its arguments (`tool`). Either can be the first the chat sees, so both map to
        // the same start and the consumer dedupes by callID.
        "session.next.tool.input.started" ->
            toolStart(data, nameKey = "name")

        "session.next.tool.called" ->
            toolStart(data, nameKey = "tool")

        "session.next.tool.success" ->
            data.str("assistantMessageID")?.let { id ->
                data.str("callID")?.let { call ->
                    EngineStreamEvent.ToolEnded(assistantMessageId = id, callId = call, ok = true, error = null)
                }
            }

        "session.next.tool.failed" ->
            data.str("assistantMessageID")?.let { id ->
                data.str("callID")?.let { call ->
                    EngineStreamEvent.ToolEnded(
                        assistantMessageId = id,
                        callId = call,
                        ok = false,
                        error = data.errorText("error"),
                    )
                }
            }

        "session.next.step.ended" ->
            data.str("assistantMessageID")?.let { id ->
                EngineStreamEvent.StepEnded(assistantMessageId = id, finish = data.str("finish"))
            }

        "session.next.step.failed" ->
            EngineStreamEvent.Failed(
                assistantMessageId = data.str("assistantMessageID"),
                error = data.errorText("error") ?: "step failed",
            )

        else -> null
    }

    private fun toolStart(data: JsonObject, nameKey: String): EngineStreamEvent? =
        data.str("assistantMessageID")?.let { id ->
            data.str("callID")?.let { call ->
                EngineStreamEvent.ToolStarted(
                    assistantMessageId = id,
                    callId = call,
                    name = data.str(nameKey) ?: data.str("tool") ?: data.str("name") ?: call,
                )
            }
        }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** `error` may be a plain string or a nested object; take a string if there is one, else null. */
    private fun JsonObject.errorText(key: String): String? = when (val e = this[key]) {
        is JsonPrimitive -> e.content.takeIf { it.isNotBlank() }
        is JsonObject -> e.str("message") ?: e.str("name") ?: e.toString()
        else -> null
    }
}
