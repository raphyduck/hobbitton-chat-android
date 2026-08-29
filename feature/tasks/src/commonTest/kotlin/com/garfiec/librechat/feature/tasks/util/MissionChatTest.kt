package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MissionChatTest {

    private fun assistant(state: MissionChatState, key: String) =
        state.turns.filterIsInstance<ChatTurn.Assistant>().single { it.key == key }

    @Test
    fun aPromptThenAStreamedReplyBecomesAUserBubbleAndAnAssistantBubble() {
        val state = missionChatFrom(
            listOf(
                EngineStreamEvent.PromptAdmitted(messageId = "msg_u", text = "Compte jusqu'à trois"),
                EngineStreamEvent.StepStarted("msg_a", agent = "build", modelId = "claude-haiku-4-5"),
                EngineStreamEvent.TextStarted("msg_a", "text-0"),
                EngineStreamEvent.TextDelta("msg_a", "text-0", "un, "),
                EngineStreamEvent.TextDelta("msg_a", "text-0", "deux, "),
                EngineStreamEvent.TextDelta("msg_a", "text-0", "trois"),
                EngineStreamEvent.TextEnded("msg_a", "text-0", "un, deux, trois"),
                EngineStreamEvent.StepEnded("msg_a", finish = "stop"),
            ),
        )

        assertEquals(2, state.turns.size)
        assertEquals(ChatTurn.User("msg_u", "Compte jusqu'à trois"), state.turns[0])
        val a = assistant(state, "msg_a")
        assertEquals(listOf<ChatPart>(ChatPart.Text("text-0", "un, deux, trois")), a.parts)
        assertFalse(state.streaming)
    }

    @Test
    fun textEndedIsAuthoritativeEvenWhenNoDeltaEverArrived() {
        // The buffering model path: text.started -> text.ended with the whole text, no delta between.
        val state = missionChatFrom(
            listOf(
                EngineStreamEvent.StepStarted("msg_a", null, null),
                EngineStreamEvent.TextStarted("msg_a", "text-0"),
                EngineStreamEvent.TextEnded("msg_a", "text-0", "Bonjour"),
                EngineStreamEvent.StepEnded("msg_a", "stop"),
            ),
        )
        assertEquals("Bonjour", (assistant(state, "msg_a").parts.single() as ChatPart.Text).text)
    }

    @Test
    fun deltasAccumulateAndThenReconcileToTheEndedText() {
        // A model that streamed "Bonj" + "our" but whose final text differs (trimmed) reconciles.
        val state = missionChatFrom(
            listOf(
                EngineStreamEvent.TextDelta("msg_a", "text-0", "Bonj"),
                EngineStreamEvent.TextDelta("msg_a", "text-0", "our "),
                EngineStreamEvent.TextEnded("msg_a", "text-0", "Bonjour"),
            ),
        )
        assertEquals("Bonjour", (assistant(state, "msg_a").parts.single() as ChatPart.Text).text)
    }

    @Test
    fun aToolIsOneCardFromStartToSuccessEvenWhenAnnouncedTwice() {
        val state = missionChatFrom(
            listOf(
                EngineStreamEvent.ToolStarted("msg_a", "call_1", "qonto_list_transactions"),
                // `tool.called` re-announces the same call; it must not spawn a second card.
                EngineStreamEvent.ToolStarted("msg_a", "call_1", "qonto_list_transactions"),
                EngineStreamEvent.ToolEnded("msg_a", "call_1", ok = true, error = null),
            ),
        )
        val tools = assistant(state, "msg_a").parts.filterIsInstance<ChatPart.Tool>()
        assertEquals(1, tools.size)
        assertEquals(ChatPart.Tool("call_1", "qonto_list_transactions", ToolState.OK), tools.single())
    }

    @Test
    fun aFailedToolIsMarkedFailed() {
        val state = missionChatFrom(
            listOf(
                EngineStreamEvent.ToolStarted("msg_a", "c", "shell"),
                EngineStreamEvent.ToolEnded("msg_a", "c", ok = false, error = "denied"),
            ),
        )
        assertEquals(ToolState.FAILED, (assistant(state, "msg_a").parts.single() as ChatPart.Tool).state)
    }

    @Test
    fun textAndToolsKeepTheirArrivalOrderWithinATurn() {
        val state = missionChatFrom(
            listOf(
                EngineStreamEvent.TextDelta("msg_a", "text-0", "Je regarde. "),
                EngineStreamEvent.ToolStarted("msg_a", "c1", "qonto_list_transactions"),
                EngineStreamEvent.ToolEnded("msg_a", "c1", ok = true, error = null),
                EngineStreamEvent.TextStarted("msg_a", "text-1"),
                EngineStreamEvent.TextDelta("msg_a", "text-1", "Trois sans justificatif."),
            ),
        )
        val parts = assistant(state, "msg_a").parts
        assertEquals(3, parts.size)
        assertTrue(parts[0] is ChatPart.Text)
        assertTrue(parts[1] is ChatPart.Tool)
        assertEquals("Trois sans justificatif.", (parts[2] as ChatPart.Text).text)
    }

    @Test
    fun aRunningMissionShowsAsStreamingUntilAStopFinish() {
        // A tool step ends with a non-stop finish: the spinner stays up mid-loop.
        val midLoop = missionChatFrom(
            listOf(
                EngineStreamEvent.PromptAdmitted("msg_u", "vas-y"),
                EngineStreamEvent.StepStarted("msg_a", null, null),
                EngineStreamEvent.ToolStarted("msg_a", "c", "shell"),
                EngineStreamEvent.ToolEnded("msg_a", "c", ok = true, error = null),
                EngineStreamEvent.StepEnded("msg_a", finish = "tool-calls"),
            ),
        )
        assertTrue(midLoop.streaming)

        val done = midLoop.reduce(EngineStreamEvent.StepEnded("msg_a", finish = "stop"))
        assertFalse(done.streaming)
    }

    @Test
    fun replayingThePromptTwiceDoesNotDoubleTheUserBubble() {
        val state = missionChatFrom(
            listOf(
                EngineStreamEvent.PromptAdmitted("msg_u", "salut"),
                EngineStreamEvent.PromptAdmitted("msg_u", "salut"),
            ),
        )
        assertEquals(1, state.turns.count { it is ChatTurn.User })
    }

    @Test
    fun aTurnFailureIsShownInPlaceAndStopsTheSpinner() {
        val state = missionChatFrom(
            listOf(
                EngineStreamEvent.StepStarted("msg_a", null, null),
                EngineStreamEvent.Failed("msg_a", "model refused"),
            ),
        )
        assertEquals("model refused", assistant(state, "msg_a").failed)
        assertFalse(state.streaming)
    }

    @Test
    fun aStreamLevelFailureSurfacesAsAStateErrorNotATurn() {
        val state = missionChatFrom(listOf(EngineStreamEvent.Failed(null, "connection lost")))
        assertEquals("connection lost", state.error)
        assertTrue(state.turns.isEmpty())
    }

    @Test
    fun twoTurnsRenderInOrderUserAssistantUserAssistant() {
        val state = missionChatFrom(
            listOf(
                EngineStreamEvent.PromptAdmitted("u1", "premier"),
                EngineStreamEvent.StepStarted("a1", null, null),
                EngineStreamEvent.TextEnded("a1", "t", "un"),
                EngineStreamEvent.StepEnded("a1", "stop"),
                EngineStreamEvent.PromptAdmitted("u2", "second"),
                EngineStreamEvent.StepStarted("a2", null, null),
                EngineStreamEvent.TextEnded("a2", "t", "deux"),
                EngineStreamEvent.StepEnded("a2", "stop"),
            ),
        )
        assertEquals(listOf("u1", "a1", "u2", "a2"), state.turns.map { it.key })
    }
}
