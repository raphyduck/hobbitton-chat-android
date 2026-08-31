package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineMessage
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EngineMessageInfo
import com.garfiec.librechat.core.model.engine.EnginePart
import com.garfiec.librechat.core.model.engine.EnginePartSnapshot
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.model.engine.EngineToolState
import com.garfiec.librechat.core.model.engine.engineHistoryEvents
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    @Test
    fun theSessionModelIsWhatTheLastAssistantTurnActuallyRanOn() {
        val kimi = EngineModelRef(providerId = "hobbitton-gateway", modelId = "kimi-k3")
        val claude = EngineModelRef(providerId = "hobbitton-gateway", modelId = "claude-x")
        val state = missionChatFrom(
            listOf(
                started("msg_u", "user"),
                EngineStreamEvent.MessageStarted("msg_a", "assistant", kimi),
                started("msg_u2", "user"),
                EngineStreamEvent.MessageStarted("msg_a2", "assistant", claude),
            ),
        )

        // The later turn wins: the model travels per message, so the session's answer to
        // "which model" is whatever it last ran on.
        assertEquals(claude, state.model)
    }

    @Test
    fun aTurnWithoutAModelLeavesTheLastKnownOneStanding() {
        val kimi = EngineModelRef(providerId = "hobbitton-gateway", modelId = "kimi-k3")
        val state = missionChatFrom(
            listOf(
                EngineStreamEvent.MessageStarted("msg_a", "assistant", kimi),
                // A user turn carries no model, and re-announcing a known message must not blank it.
                started("msg_u", "user"),
                started("msg_a", "assistant"),
            ),
        )

        assertEquals(kimi, state.model)
    }

    @Test
    fun aToolCarriesItsArgumentsAndItsOutput() {
        val state = missionChatFrom(
            listOf(
                started("msg_a", "assistant"),
                part(
                    "msg_a",
                    "p1",
                    EnginePartSnapshot(
                        type = "tool",
                        tool = "memoire_lire",
                        status = "completed",
                        input = buildJsonObject {
                            put("chemin", JsonPrimitive("journal/2026-08.md"))
                            put("limite", JsonPrimitive(20))
                        },
                        output = "trois entrées",
                    ),
                ),
            ),
        )

        val tool = state.turns.single().let { (it as ChatTurn.Assistant).parts }
            .filterIsInstance<ChatPart.Tool>().single()
        assertEquals(
            listOf(
                ToolArgument("chemin", "journal/2026-08.md"),
                // A non-string argument keeps its JSON form; a string one loses its quotes.
                ToolArgument("limite", "20"),
            ),
            tool.arguments,
        )
        assertEquals("trois entrées", tool.output)
    }

    @Test
    fun aToolWithAnEmptyOutputReportsNoneRatherThanAnEmptyDrawer() {
        val state = missionChatFrom(
            listOf(
                started("msg_a", "assistant"),
                part("msg_a", "p1", EnginePartSnapshot(type = "tool", tool = "t", status = "completed", output = "")),
            ),
        )

        val tool = (state.turns.single() as ChatTurn.Assistant).parts
            .filterIsInstance<ChatPart.Tool>().single()
        assertEquals(null, tool.output)
        assertTrue(tool.arguments.isEmpty())
    }

    @Test
    fun `une part fichier devient la piece jointe du tour`() {
        // Ignorée en silence jusqu'au 31/08/2026 : un message envoyé avec une photo n'affichait
        // que sa prose, et rien à l'écran ne disait que la photo était partie avec.
        val state = missionChatFrom(
            listOf(
                EngineStreamEvent.MessageStarted("m1", "user"),
                EngineStreamEvent.PartUpdated(
                    "m1",
                    "p1",
                    EnginePartSnapshot(type = "file", mime = "image/jpeg", url = "data:image/jpeg;base64,AAAA"),
                ),
            ),
        )

        val part = state.turns.single().let { (it as ChatTurn.User).parts.single() }
        assertEquals(
            ChatPart.Attachment("p1", "image/jpeg", "data:image/jpeg;base64,AAAA", null),
            part,
        )
    }

    @Test
    fun `une part fichier sans url est ignoree plutot que rendue vide`() {
        val state = missionChatFrom(
            listOf(
                EngineStreamEvent.MessageStarted("m1", "user"),
                EngineStreamEvent.PartUpdated("m1", "p1", EnginePartSnapshot(type = "file", mime = "image/jpeg")),
            ),
        )

        assertEquals(emptyList(), (state.turns.single() as ChatTurn.User).parts)
    }
}

/**
 * Cutting a turn into what one reads and what one merely checks.
 *
 * The tab shipped flat on 30/08/2026: nine tool calls and a paragraph of thinking buried the two
 * sentences that were the point.
 */
class ChatBlockTest {

    private fun text(id: String, t: String) = ChatPart.Text(id, t)
    private fun tool(id: String, state: ToolState = ToolState.OK) = ChatPart.Tool(id, "memoire_lire", state)
    private fun thinking(id: String) = ChatPart.Reasoning(id, "je regarde")

    @Test
    fun reasoningAndToolsFoldTogetherAndProseStaysOut() {
        val blocks = listOf(thinking("r1"), tool("t1"), text("p1", "Trois sans justificatif.")).asBlocks()

        assertEquals(2, blocks.size)
        assertEquals(listOf("r1", "t1"), (blocks[0] as ChatBlock.Activity).parts.map { it.id })
        assertEquals("Trois sans justificatif.", (blocks[1] as ChatBlock.Prose).part.text)
    }

    @Test
    fun aToolBetweenTwoParagraphsBelongsToWhatFollowsIt() {
        // Merging across prose would reorder the turn: the second paragraph would end up above a
        // call that produced it.
        val blocks = listOf(text("p1", "un"), tool("t1"), text("p2", "deux")).asBlocks()

        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is ChatBlock.Prose)
        assertTrue(blocks[1] is ChatBlock.Activity)
        assertTrue(blocks[2] is ChatBlock.Prose)
    }

    @Test
    fun anEmptyTextPartDoesNotCutTheActivityRunInTwo() {
        // An empty text part is a part awaiting its deltas, not a paragraph — it would otherwise
        // open a second fold mid-thought, live, while the answer is still arriving.
        val blocks = listOf(tool("t1"), text("p1", ""), tool("t2")).asBlocks()

        assertEquals(1, blocks.size)
        assertEquals(listOf("t1", "t2"), (blocks.single() as ChatBlock.Activity).parts.map { it.id })
    }

    @Test
    fun aTurnOfPureProseHasNothingToFold() {
        val blocks = listOf(text("p1", "bonsoir")).asBlocks()

        assertEquals(1, blocks.size)
        assertTrue(blocks.single() is ChatBlock.Prose)
    }

    @Test
    fun theHeaderKnowsWhatItIsHiding() {
        val block = listOf(thinking("r1"), tool("t1"), tool("t2")).asBlocks().single() as ChatBlock.Activity

        assertEquals(2, block.toolCount())
        assertTrue(block.hasReasoning())
    }

    @Test
    fun aRunningOrFailedToolIsVisibleFromTheFoldedHeader() {
        // The two states a fold must never hide: a mission waiting on a tool looks exactly like one
        // that has stopped, and a failure folded away is a failure nobody reads.
        val running = listOf(tool("t1", ToolState.RUNNING)).asBlocks().single() as ChatBlock.Activity
        val failed = listOf(tool("t1", ToolState.FAILED)).asBlocks().single() as ChatBlock.Activity
        val fine = listOf(tool("t1", ToolState.OK)).asBlocks().single() as ChatBlock.Activity

        assertTrue(running.isRunning())
        assertTrue(failed.hasFailure())
        assertFalse(fine.isRunning() || fine.hasFailure())
    }

    @Test
    fun theBlockKeyIsStableSoTheFoldSurvivesTheNextToken() {
        // Keyed on the first part's id, not the index: a delta appending to the answer must not
        // re-key the block and snap it shut under the reader's thumb.
        val before = listOf(tool("t1"), thinking("r1")).asBlocks().single() as ChatBlock.Activity
        val after = listOf(tool("t1"), thinking("r1"), tool("t2")).asBlocks().single() as ChatBlock.Activity

        assertEquals(before.key, after.key)
    }


    @Test
    fun `une piece jointe fait son propre bloc, jamais plie dans l'activite`() {
        // C'est du contenu, pas du processus : la plier avec les outils la cacherait par défaut.
        val blocks = listOf(
            ChatPart.Tool("t1", "memoire_lire", ToolState.OK),
            ChatPart.Attachment("a1", "image/jpeg", "data:image/jpeg;base64,AAAA", null),
            ChatPart.Text("x1", "voila"),
        ).asBlocks()

        assertEquals(3, blocks.size)
        assertIs<ChatBlock.Activity>(blocks[0])
        assertIs<ChatBlock.Media>(blocks[1])
        assertIs<ChatBlock.Prose>(blocks[2])
    }
}
