package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineMessage
import com.garfiec.librechat.core.model.engine.EngineMessageInfo
import com.garfiec.librechat.core.model.engine.EnginePart
import com.garfiec.librechat.core.model.engine.EnginePartSnapshot
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.model.engine.EngineToolState
import com.garfiec.librechat.core.model.engine.engineHistoryEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MissionChatTest {

    private fun started(id: String, role: String) = EngineStreamEvent.MessageStarted(id, role)
    private fun part(msg: String, id: String, snapshot: EnginePartSnapshot) =
        EngineStreamEvent.PartUpdated(msg, id, snapshot)
    private fun delta(msg: String, id: String, text: String) =
        EngineStreamEvent.PartDelta(msg, id, "text", text)

    @Test
    fun aTurnStreamsInByDeltasAndIsClosedByItsSnapshot() {
        val state = missionChatFrom(
            listOf(
                started("msg_u", "user"),
                part("msg_u", "p0", EnginePartSnapshot(type = "text", text = "Compte jusqu'à trois")),
                started("msg_a", "assistant"),
                part("msg_a", "p1", EnginePartSnapshot(type = "text", text = "")),
                delta("msg_a", "p1", "Un, "),
                delta("msg_a", "p1", "deux, "),
                delta("msg_a", "p1", "trois"),
                part("msg_a", "p1", EnginePartSnapshot(type = "text", text = "Un, deux, trois")),
                EngineStreamEvent.Idle,
            ),
        )

        assertEquals(2, state.turns.size)
        assertEquals("Compte jusqu'à trois", state.turns[0].text())
        assertEquals("Un, deux, trois", state.turns[1].text())
        assertFalse(state.streaming)
    }

    @Test
    fun aSnapshotIsAuthoritativeAndOverwritesWhatTheDeltasBuilt() {
        // The engine closes a delta run with the whole text; if the two disagree — a dropped frame —
        // the snapshot wins, because it is the one the transcript will also show.
        val state = missionChatFrom(
            listOf(
                delta("msg_a", "p1", "Bonj"),
                part("msg_a", "p1", EnginePartSnapshot(type = "text", text = "Bonjour")),
            ),
        )
        assertEquals("Bonjour", state.turns.single().text())
    }

    @Test
    fun aDeltaBeforeItsPartStillLandsRatherThanBeingDropped() {
        val state = missionChatFrom(listOf(delta("msg_a", "p1", "déjà là")))
        assertEquals("déjà là", state.turns.single().text())
    }

    @Test
    fun replayingTheSameMessageAndPartDoesNotDuplicateThem() {
        // Seeding from the transcript and then tailing the feed shows the same ids twice; the seam
        // must not become a second bubble.
        val events = listOf(
            started("msg_a", "assistant"),
            part("msg_a", "p1", EnginePartSnapshot(type = "text", text = "un")),
        )
        val state = missionChatFrom(events + events)
        assertEquals(1, state.turns.size)
        assertEquals(1, (state.turns.single() as ChatTurn.Assistant).parts.size)
    }

    @Test
    fun toolStatusDecidesTheCardAndAnAbsentStatusIsNotASuccess() {
        val state = missionChatFrom(
            listOf(
                part("msg_a", "t1", EnginePartSnapshot(type = "tool", tool = "qonto_list", status = "completed")),
                part("msg_a", "t2", EnginePartSnapshot(type = "tool", tool = "shell", status = "error")),
                part("msg_a", "t3", EnginePartSnapshot(type = "tool", tool = "read", status = null)),
            ),
        )
        val tools = (state.turns.single() as ChatTurn.Assistant).parts.filterIsInstance<ChatPart.Tool>()
        assertEquals(listOf(ToolState.OK, ToolState.FAILED, ToolState.RUNNING), tools.map { it.state })
    }

    @Test
    fun bookkeepingPartsAreNotRendered() {
        val state = missionChatFrom(
            listOf(
                started("msg_a", "assistant"),
                part("msg_a", "s1", EnginePartSnapshot(type = "step-start")),
                part("msg_a", "s2", EnginePartSnapshot(type = "step-finish")),
            ),
        )
        assertTrue((state.turns.single() as ChatTurn.Assistant).parts.isEmpty())
    }

    @Test
    fun onlyTextDeltasAreAppended() {
        // The engine also deltas tool input; those must not print themselves into the answer.
        val state = missionChatFrom(
            listOf(
                part("msg_a", "p1", EnginePartSnapshot(type = "text", text = "réponse")),
                EngineStreamEvent.PartDelta("msg_a", "p1", "input", "{\"chemin\":"),
            ),
        )
        assertEquals("réponse", state.turns.single().text())
    }

    @Test
    fun idleEndsTheSpinnerAndADeltaStartsItAgain() {
        val idle = missionChatFrom(listOf(started("msg_a", "assistant"), EngineStreamEvent.Idle))
        assertFalse(idle.streaming)
        assertTrue(idle.reduce(delta("msg_a", "p1", "encore")).streaming)
    }

    @Test
    fun aFetchedTranscriptFoldsIntoTheSameConversationAsALiveTurn() {
        // The seeding path: what `GET /session/{id}/message` returns, replayed as events.
        val history = engineHistoryEvents(
            listOf(
                EngineMessage(
                    info = EngineMessageInfo(id = "m1", role = "user"),
                    parts = listOf(EnginePart(id = "p1", type = "text", text = "Relève les transactions")),
                ),
                EngineMessage(
                    info = EngineMessageInfo(id = "m2", role = "assistant"),
                    parts = listOf(
                        EnginePart(id = "p2", type = "step-start"),
                        EnginePart(
                            id = "p3",
                            type = "tool",
                            tool = "qonto_list_transactions",
                            callId = "c1",
                            state = EngineToolState(status = "completed"),
                        ),
                        EnginePart(id = "p4", type = "text", text = "Trois sans justificatif."),
                    ),
                ),
            ),
        )

        val state = missionChatFrom(history)
        assertEquals(2, state.turns.size)
        assertEquals("Relève les transactions", state.turns[0].text())
        val assistant = state.turns[1] as ChatTurn.Assistant
        assertEquals("Trois sans justificatif.", assistant.text())
        val tool = assistant.parts.filterIsInstance<ChatPart.Tool>().single()
        assertEquals("qonto_list_transactions", tool.name)
        assertEquals(ToolState.OK, tool.state)
    }

    @Test
    fun aPartWithoutItsOwnIdStillStandsApartFromItsNeighbour() {
        val history = engineHistoryEvents(
            listOf(
                EngineMessage(
                    info = EngineMessageInfo(id = "m1", role = "assistant"),
                    parts = listOf(
                        EnginePart(type = "text", text = "premier"),
                        EnginePart(type = "text", text = "second"),
                    ),
                ),
            ),
        )
        val assistant = missionChatFrom(history).turns.single() as ChatTurn.Assistant
        assertEquals(2, assistant.parts.size)
        assertEquals("premier\n\nsecond", assistant.text())
    }
}
