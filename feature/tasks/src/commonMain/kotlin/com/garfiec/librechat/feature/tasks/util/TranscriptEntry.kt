package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineMessage

/**
 * One displayable line of a mission's transcript: who said it, whether it is a tool step or prose,
 * and the text to show.
 *
 * A **pure** flattening of the engine's messages so it can be asserted directly — this module has no
 * Compose test harness, and the filtering rules (which part types show, which are noise) are exactly
 * the kind of thing that rots silently when it lives inside a composable.
 */
data class TranscriptEntry(
    val role: String,
    val kind: Kind,
    val text: String,
) {
    enum class Kind { TEXT, TOOL }
}

/**
 * Turns a session's messages into the lines the row renders when expanded.
 *
 * The engine's parts carry more types than a person needs to read: `step-start`, `step-finish` and
 * the like are the run's own bookkeeping. Only two are shown — a `text` part is prose, a `tool` part
 * is a step (named by the tool it called) — and blank text is dropped so an assistant turn that was
 * only a tool call does not render an empty line above it. Anything else is skipped rather than
 * shown as a raw type name.
 */
fun missionTranscript(messages: List<EngineMessage>): List<TranscriptEntry> =
    messages.flatMap { message ->
        val role = message.info.role
        message.parts.mapNotNull { part ->
            when (part.type) {
                "text" -> part.text?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { TranscriptEntry(role, TranscriptEntry.Kind.TEXT, it) }

                "tool" -> (part.tool ?: part.text)?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { TranscriptEntry(role, TranscriptEntry.Kind.TOOL, it) }

                else -> null
            }
        }
    }
