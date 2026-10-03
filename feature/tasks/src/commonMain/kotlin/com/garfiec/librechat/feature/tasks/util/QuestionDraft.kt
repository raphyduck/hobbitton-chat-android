package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineQuestionRequest

/**
 * The person's answer to a question request, while they fill the form.
 *
 * One entry per question, in order: the options ticked ([picked]) and the free text ([typed]). The
 * reply is the labels, with the typed text as one more label when there is some; the engine passes
 * the strings to the model verbatim and checks none of them against the options.
 *
 * A single-choice question holds one answer: ticking an option clears the free text, typing clears
 * the tick, so what is sent is what the form shows selected. A multiple-choice question keeps both.
 *
 * Pure, so the rules are pinned by `QuestionDraftTest` rather than by a screen.
 */
data class QuestionDraft(
    val requestId: String,
    val picked: List<List<String>>,
    val typed: List<String>,
) {
    fun pick(index: Int, label: String, multiple: Boolean): QuestionDraft {
        if (index !in picked.indices) return this
        val current = picked[index]
        val next = when {
            !multiple -> listOf(label)
            label in current -> current - label
            else -> current + label
        }
        return copy(
            picked = picked.replaced(index, next),
            typed = if (multiple) typed else typed.replaced(index, ""),
        )
    }

    fun type(index: Int, text: String, multiple: Boolean): QuestionDraft {
        if (index !in typed.indices) return this
        return copy(
            typed = typed.replaced(index, text),
            picked = if (multiple || text.isBlank()) picked else picked.replaced(index, emptyList()),
        )
    }

    /** The reply, one list of labels per question. */
    fun answers(): List<List<String>> = picked.mapIndexed { index, labels ->
        labels + listOfNotNull(typed.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() })
    }

    /** Every question has something to send. Until then the form offers « Dismiss », not « Send ». */
    fun isComplete(): Boolean = answers().isNotEmpty() && answers().all { it.isNotEmpty() }

    companion object {
        fun blank(request: EngineQuestionRequest) = QuestionDraft(
            requestId = request.id,
            picked = List(request.questions.size) { emptyList() },
            typed = List(request.questions.size) { "" },
        )
    }
}

private fun <T> List<T>.replaced(index: Int, value: T): List<T> = mapIndexed { i, old -> if (i == index) value else old }
