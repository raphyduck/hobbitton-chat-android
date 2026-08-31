package com.garfiec.librechat.core.network.engine

import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EnginePartSnapshot
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.network.sse.SseEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turns one frame of the engine's **global** event feed (`GET /event`) into a domain event.
 *
 * The feed is global, not per-session: every frame carries `properties.sessionID` and a listener
 * keeps the ones it asked for. [parse] therefore reports the session id alongside the event and
 * leaves the filtering to [EngineStreamClient], which is the only place that knows which session is
 * on screen.
 *
 * The four shapes below were captured off the live engine on 29/08/2026 during a real turn; every
 * other type on the feed (`session.status`, `session.diff`, `message.removed`…) maps to null and is
 * dropped. Pure and platform-free, so the mapping is pinned by tests rather than by a live run.
 */
class EngineEventParser(private val json: Json) {

    data class Parsed(val sessionId: String?, val event: EngineStreamEvent?)

    /** Null when the frame is not a JSON object with a `type` — a comment, or a malformed line. */
    fun parse(frame: SseEvent): Parsed? {
        if (frame.data.isBlank()) return null
        val root = runCatching { json.parseToJsonElement(frame.data) }.getOrNull() as? JsonObject ?: return null
        val type = root.str("type") ?: return null
        val props = root["properties"] as? JsonObject ?: return null
        return Parsed(sessionId = props.str("sessionID"), event = map(type, props))
    }

    private fun map(type: String, props: JsonObject): EngineStreamEvent? = when (type) {
        "message.updated" -> {
            val info = props["info"] as? JsonObject
            val id = info?.str("id")
            val role = info?.str("role")
            val modelId = info?.str("modelID")
            val providerId = info?.str("providerID")
            if (id != null && role != null) {
                EngineStreamEvent.MessageStarted(
                    messageId = id,
                    role = role,
                    // Only assistant messages carry them; a user turn simply has neither.
                    model = if (modelId != null && providerId != null) {
                        EngineModelRef(providerId = providerId, modelId = modelId)
                    } else {
                        null
                    },
                )
            } else {
                null
            }
        }

        "message.part.updated" -> {
            val part = props["part"] as? JsonObject
            val messageId = part?.str("messageID")
            val partId = part?.str("id")
            val partType = part?.str("type")
            if (part != null && messageId != null && partId != null && partType != null) {
                EngineStreamEvent.PartUpdated(
                    messageId = messageId,
                    partId = partId,
                    part = (part["state"] as? JsonObject).let { state ->
                        EnginePartSnapshot(
                            type = partType,
                            text = part.str("text"),
                            tool = part.str("tool"),
                            callId = part.str("callID"),
                            status = state?.str("status"),
                            // Measured on the live engine 30/08/2026: a tool's `state` carries its
                            // `input` object and its `output` text. Reading only `status` is what
                            // left the conversation able to say that a tool ran and unable to say
                            // what it did.
                            input = state?.get("input"),
                            output = state?.str("output"),
                            mime = part.str("mime"),
                            url = part.str("url"),
                            filename = part.str("filename"),
                        )
                    },
                )
            } else {
                null
            }
        }

        "message.part.delta" -> {
            val messageId = props.str("messageID")
            val partId = props.str("partID")
            if (messageId != null && partId != null) {
                EngineStreamEvent.PartDelta(
                    messageId = messageId,
                    partId = partId,
                    field = props.str("field") ?: "",
                    delta = props.str("delta") ?: "",
                )
            } else {
                null
            }
        }

        "session.idle" -> EngineStreamEvent.Idle

        else -> null
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
