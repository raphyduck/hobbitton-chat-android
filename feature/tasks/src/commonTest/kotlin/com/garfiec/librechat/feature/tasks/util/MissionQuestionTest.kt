package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineQuestionInfo
import com.garfiec.librechat.core.model.engine.EngineQuestionOption
import com.garfiec.librechat.core.model.engine.EngineQuestionRequest
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The questions a conversation waits on, and the answer the form builds. */
class MissionQuestionTest {

    private fun request(id: String, vararg questions: EngineQuestionInfo) =
        EngineQuestionRequest(id = id, sessionId = "ses_1", questions = questions.toList())

    private fun single(text: String = "Quel compte ?") = EngineQuestionInfo(
        question = text,
        header = "Compte",
        options = listOf(EngineQuestionOption("Qonto", "pro"), EngineQuestionOption("CMB", "privée")),
    )

    private fun several() = single("Quels mois ?").copy(
        multiple = true,
        options = listOf(EngineQuestionOption("Juillet"), EngineQuestionOption("Août")),
    )

    @Test
    fun anAskedQuestionWaitsUntilItIsClosed() {
        val asked = MissionChatState().reduce(EngineStreamEvent.QuestionAsked(request("que_1", single())))
        assertEquals(listOf("que_1"), asked.questions.map { it.id })

        val closed = asked.reduce(EngineStreamEvent.QuestionClosed("que_1"))
        assertTrue(closed.questions.isEmpty())
    }

    @Test
    fun theSameQuestionAnnouncedTwiceIsListedOnce() {
        val event = EngineStreamEvent.QuestionAsked(request("que_1", single()))
        val state = MissionChatState().reduce(event).reduce(event)
        assertEquals(1, state.questions.size)
    }

    @Test
    fun anIdleSessionHasNothingWaiting() {
        // A stopped turn drops its question without a `question.rejected`: the form must not stay.
        val state = MissionChatState()
            .reduce(EngineStreamEvent.QuestionAsked(request("que_1", single())))
            .reduce(EngineStreamEvent.Idle)
        assertTrue(state.questions.isEmpty())
    }

    @Test
    fun pendingQuestionsReadFromTheEngineMergeUnderTheFeed() {
        val live = MissionChatState().reduce(EngineStreamEvent.QuestionAsked(request("que_2", single())))
        val merged = live.withPendingQuestions(listOf(request("que_1", single()), request("que_2", single())))
        assertEquals(listOf("que_2", "que_1"), merged.questions.map { it.id })
    }

    @Test
    fun aSingleChoiceHoldsOneAnswerOptionOrTyped() {
        val blank = QuestionDraft.blank(request("que_1", single()))
        assertFalse(blank.isComplete())

        val picked = blank.pick(0, "Qonto", multiple = false).pick(0, "CMB", multiple = false)
        assertEquals(listOf(listOf("CMB")), picked.answers())

        // Typing replaces the tick, so what is sent is what the form shows.
        val typed = picked.type(0, "  Le compte joint ", multiple = false)
        assertEquals(listOf(listOf("Le compte joint")), typed.answers())

        // And ticking again clears the typed answer.
        assertEquals(listOf(listOf("Qonto")), typed.pick(0, "Qonto", multiple = false).answers())
    }

    @Test
    fun aMultipleChoiceKeepsEveryTickAndTheTypedAnswer() {
        val draft = QuestionDraft.blank(request("que_1", several()))
            .pick(0, "Juillet", multiple = true)
            .pick(0, "Août", multiple = true)
            .type(0, "Septembre", multiple = true)
            .pick(0, "Juillet", multiple = true)
        assertEquals(listOf(listOf("Août", "Septembre")), draft.answers())
    }

    @Test
    fun sendIsOfferedOnlyOnceEveryQuestionHasAnAnswer() {
        val draft = QuestionDraft.blank(request("que_1", single(), several()))
            .pick(0, "Qonto", multiple = false)
        assertFalse(draft.isComplete())
        assertTrue(draft.pick(1, "Août", multiple = true).isComplete())
        // Blank text is not an answer.
        assertFalse(draft.type(1, "   ", multiple = true).isComplete())
    }

    @Test
    fun anIndexOutsideTheRequestChangesNothing() {
        val draft = QuestionDraft.blank(request("que_1", single()))
        assertEquals(draft, draft.pick(3, "Qonto", multiple = false).type(-1, "x", multiple = false))
    }
}
