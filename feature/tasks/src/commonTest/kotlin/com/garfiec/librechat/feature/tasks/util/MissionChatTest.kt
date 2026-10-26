package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineError
import com.garfiec.librechat.core.model.engine.EngineErrorData
import com.garfiec.librechat.core.model.engine.EngineMessage
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EngineMessageInfo
import com.garfiec.librechat.core.model.engine.EnginePart
import com.garfiec.librechat.core.model.engine.EnginePartSnapshot
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.model.engine.EngineTokens
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
    fun `une reponse refusee par le moteur montre sa raison au lieu d'un blanc`() {
        val live = missionChatFrom(
            listOf(
                started("msg_1u", "user"),
                part("msg_1u", "p0", EnginePartSnapshot(type = "text", text = "Mon agenda ?")),
                started("msg_2a", "assistant"),
                EngineStreamEvent.MessageStarted("msg_2a", "assistant", error = "ExceededBudget"),
                EngineStreamEvent.Idle,
            ),
        )
        val turn = assertIs<ChatTurn.Assistant>(live.turns.last())
        assertEquals("ExceededBudget", turn.error)

        // Le même tour relu depuis le transcript.
        val replayed = missionChatFrom(
            engineHistoryEvents(
                listOf(
                    EngineMessage(
                        info = EngineMessageInfo(
                            id = "msg_2a",
                            role = "assistant",
                            error = EngineError(name = "APIError", data = EngineErrorData("""{"error":{"message":"Key over budget"}}""")),
                        ),
                    ),
                ),
            ),
        )
        assertEquals("Key over budget", assertIs<ChatTurn.Assistant>(replayed.turns.single()).error)
        assertEquals("Key over budget", assertIs<ChatTurn.Assistant>(replayed.turns.mergedAssistantRuns().single()).error)
    }

    @Test
    fun aTurnStreamsInByDeltasAndIsClosedByItsSnapshot() {
        val state = missionChatFrom(
            listOf(
                started("msg_1u", "user"),
                part("msg_1u", "p0", EnginePartSnapshot(type = "text", text = "Compte jusqu'à trois")),
                started("msg_2a", "assistant"),
                part("msg_2a", "p1", EnginePartSnapshot(type = "text", text = "")),
                delta("msg_2a", "p1", "Un, "),
                delta("msg_2a", "p1", "deux, "),
                delta("msg_2a", "p1", "trois"),
                part("msg_2a", "p1", EnginePartSnapshot(type = "text", text = "Un, deux, trois")),
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
                delta("msg_2a", "p1", "Bonj"),
                part("msg_2a", "p1", EnginePartSnapshot(type = "text", text = "Bonjour")),
            ),
        )
        assertEquals("Bonjour", state.turns.single().text())
    }

    @Test
    fun aDeltaOnAPartNeverAnnouncedIsTakenForReasoningUntilItsSnapshot() {
        // 26/10/2026: such a part may be reasoning, and drawn as prose it filled the screen, then
        // folded away when its snapshot came. The view model reads the transcript instead.
        val state = missionChatFrom(listOf(delta("msg_2a", "p1", "déjà là")))
        val turn = state.turns.single() as ChatTurn.Assistant
        assertEquals(listOf<ChatPart>(ChatPart.Reasoning("p1", "déjà là")), turn.parts)
        assertEquals("", turn.text())
        assertTrue(state.streaming)
        assertTrue(state.knowsPart("msg_2a", "p1"))
        // The person's own words can only be prose.
        val user = missionChatFrom(listOf(started("msg_1u", "user"), delta("msg_1u", "p0", "Bonjour")))
        assertEquals("Bonjour", user.turns.single().text())
    }

    @Test
    fun reasoningStreamsInsideItsFoldNeverAsProse() {
        val state = missionChatFrom(
            listOf(
                started("msg_2a", "assistant"),
                part("msg_2a", "r1", EnginePartSnapshot(type = "reasoning", text = "")),
                delta("msg_2a", "r1", "Je vais "),
                delta("msg_2a", "r1", "naviguer."),
            ),
        )
        val parts = (state.turns.single() as ChatTurn.Assistant).parts
        assertEquals(listOf<ChatPart>(ChatPart.Reasoning("r1", "Je vais naviguer.")), parts)
        assertTrue(parts.asBlocks().single() is ChatBlock.Activity)
        assertTrue(state.knowsPart("msg_2a", "r1"))
    }

    @Test
    fun aMessageSeenLateTakesItsPlaceInTheOrderOfTheIds() {
        // The live feed names the running turn first; the transcript read brings the older ones
        // after it. Folded in arrival order they used to land after the live turn.
        val state = missionChatFrom(
            listOf(
                part("msg_0003", "p3", EnginePartSnapshot(type = "reasoning", text = "")),
                started("msg_0001", "user"),
                part("msg_0001", "p1", EnginePartSnapshot(type = "text", text = "Question")),
                started("msg_0002", "assistant"),
                part("msg_0002", "p2", EnginePartSnapshot(type = "text", text = "Réponse")),
                started("msg_0004", "assistant"),
            ),
        )
        assertEquals(listOf("msg_0001", "msg_0002", "msg_0003", "msg_0004"), state.turns.map { it.key })
    }

    @Test
    fun aDeltaBeforeItsPartIsCaughtUpByTheSnapshot() {
        val state = missionChatFrom(
            listOf(
                delta("msg_2a", "p1", "déjà "),
                part("msg_2a", "p1", EnginePartSnapshot(type = "text", text = "déjà là")),
            ),
        )
        assertEquals("déjà là", state.turns.single().text())
    }

    @Test
    fun replayingTheSameMessageAndPartDoesNotDuplicateThem() {
        // Seeding from the transcript and then tailing the feed shows the same ids twice; the seam
        // must not become a second bubble.
        val events = listOf(
            started("msg_2a", "assistant"),
            part("msg_2a", "p1", EnginePartSnapshot(type = "text", text = "un")),
        )
        val state = missionChatFrom(events + events)
        assertEquals(1, state.turns.size)
        assertEquals(1, (state.turns.single() as ChatTurn.Assistant).parts.size)
    }

    @Test
    fun toolStatusDecidesTheCardAndAnAbsentStatusIsNotASuccess() {
        val state = missionChatFrom(
            listOf(
                part("msg_2a", "t1", EnginePartSnapshot(type = "tool", tool = "qonto_list", status = "completed")),
                part("msg_2a", "t2", EnginePartSnapshot(type = "tool", tool = "shell", status = "error")),
                part("msg_2a", "t3", EnginePartSnapshot(type = "tool", tool = "read", status = null)),
            ),
        )
        val tools = (state.turns.single() as ChatTurn.Assistant).parts.filterIsInstance<ChatPart.Tool>()
        assertEquals(listOf(ToolState.OK, ToolState.FAILED, ToolState.RUNNING), tools.map { it.state })
    }

    @Test
    fun bookkeepingPartsAreNotRendered() {
        val state = missionChatFrom(
            listOf(
                started("msg_2a", "assistant"),
                part("msg_2a", "s1", EnginePartSnapshot(type = "step-start")),
                part("msg_2a", "s2", EnginePartSnapshot(type = "step-finish")),
            ),
        )
        assertTrue((state.turns.single() as ChatTurn.Assistant).parts.isEmpty())
    }

    @Test
    fun onlyTextDeltasAreAppended() {
        // The engine also deltas tool input; those must not print themselves into the answer.
        val state = missionChatFrom(
            listOf(
                part("msg_2a", "p1", EnginePartSnapshot(type = "text", text = "réponse")),
                EngineStreamEvent.PartDelta("msg_2a", "p1", "input", "{\"chemin\":"),
            ),
        )
        assertEquals("réponse", state.turns.single().text())
    }

    @Test
    fun idleEndsTheSpinnerAndADeltaStartsItAgain() {
        val idle = missionChatFrom(listOf(started("msg_2a", "assistant"), EngineStreamEvent.Idle))
        assertFalse(idle.streaming)
        assertTrue(idle.reduce(delta("msg_2a", "p1", "encore")).streaming)
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
                started("msg_1u", "user"),
                EngineStreamEvent.MessageStarted("msg_2a", "assistant", kimi),
                started("msg_3u", "user"),
                EngineStreamEvent.MessageStarted("msg_4a", "assistant", claude),
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
                EngineStreamEvent.MessageStarted("msg_2a", "assistant", kimi),
                // A user turn carries no model, and re-announcing a known message must not blank it.
                started("msg_1u", "user"),
                started("msg_2a", "assistant"),
            ),
        )

        assertEquals(kimi, state.model)
    }

    @Test
    fun aToolCarriesItsArgumentsAndItsOutput() {
        val state = missionChatFrom(
            listOf(
                started("msg_2a", "assistant"),
                part(
                    "msg_2a",
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
    fun aDirectoryCallShowsTheRealToolAndItsOwnArguments() {
        val state = missionChatFrom(
            listOf(
                started("msg_2a", "assistant"),
                part(
                    "msg_2a",
                    "p1",
                    EnginePartSnapshot(
                        type = "tool",
                        tool = "planificateur_annuaire_appeler",
                        status = "completed",
                        input = buildJsonObject {
                            put("outil", JsonPrimitive("ssh_ssh_exec"))
                            put("arguments", buildJsonObject { put("command", JsonPrimitive("uptime")) })
                        },
                    ),
                ),
            ),
        )

        val tool = (state.turns.single() as ChatTurn.Assistant).parts
            .filterIsInstance<ChatPart.Tool>().single()
        assertEquals("ssh_ssh_exec", tool.name)
        assertEquals(listOf(ToolArgument("command", "uptime")), tool.arguments)
    }

    @Test
    fun aDirectoryCallWithoutAToolNameKeepsTheDirectoryName() {
        val state = missionChatFrom(
            listOf(
                started("msg_2a", "assistant"),
                part(
                    "msg_2a",
                    "p1",
                    EnginePartSnapshot(type = "tool", tool = "planificateur_annuaire_appeler", status = "error"),
                ),
            ),
        )

        val tool = (state.turns.single() as ChatTurn.Assistant).parts
            .filterIsInstance<ChatPart.Tool>().single()
        assertEquals("planificateur_annuaire_appeler", tool.name)
    }

    @Test
    fun aToolWithAnEmptyOutputReportsNoneRatherThanAnEmptyDrawer() {
        val state = missionChatFrom(
            listOf(
                started("msg_2a", "assistant"),
                part("msg_2a", "p1", EnginePartSnapshot(type = "tool", tool = "t", status = "completed", output = "")),
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
    fun `un texte synthetique n'est pas montre comme un message de la personne`() {
        // 19/10/2026 : la consigne de plafond du planificateur, rejouée dans une conversation de
        // décision, s'affichait comme un message de Raphaël.
        val state = missionChatFrom(
            listOf(
                EngineStreamEvent.MessageStarted("m1", "user"),
                EngineStreamEvent.PartUpdated("m1", "p1", EnginePartSnapshot(type = "text", text = "PLAFOND ATTEINT", synthetic = true)),
            ),
        )

        assertEquals(emptyList(), (state.turns.single() as ChatTurn.User).parts)
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

    @Test
    fun `le contexte est celui du dernier appel du modele, pas la somme des tours`() {
        val tokens = { input: Long -> EngineTokens(input = input, output = 100) }
        var state = MissionChatState()
            .reduce(started("m1u", "user"))
            .reduce(EngineStreamEvent.MessageStarted("m2a", "assistant", tokens = tokens(10_000)))
            .reduce(started("m3u", "user"))
        assertEquals(10_100L, state.contextTokens)
        // Le tour suivant s'annonce d'abord à zéro (son premier appel tourne) : le contexte reste.
        state = state.reduce(EngineStreamEvent.MessageStarted("m4a", "assistant", tokens = EngineTokens()))
        assertEquals(10_100L, state.contextTokens)
        // Puis le moteur réécrit les jetons du message à chaque étape.
        state = state.reduce(EngineStreamEvent.MessageStarted("m4a", "assistant", tokens = tokens(25_000)))
        assertEquals(25_100L, state.contextTokens)
    }

    @Test
    fun `un historique rejoue donne le contexte de son dernier message`() {
        val messages = listOf(
            EngineMessage(EngineMessageInfo(id = "m2a", role = "assistant", tokens = EngineTokens(input = 5_000))),
            EngineMessage(EngineMessageInfo(id = "m3u", role = "user")),
            EngineMessage(EngineMessageInfo(id = "m4a", role = "assistant", tokens = EngineTokens(input = 9_000))),
        )
        val state = engineHistoryEvents(messages).fold(MissionChatState()) { s, e -> s.reduce(e) }
        assertEquals(9_000L, state.contextTokens)
    }

    @Test
    fun `un compactage rejoue montre sa ligne, cache le resume et dit ce qu'il a economise`() {
        val messages = listOf(
            EngineMessage(EngineMessageInfo(id = "m2a", role = "assistant", tokens = EngineTokens(input = 900_000, output = 2_000))),
            EngineMessage(
                EngineMessageInfo(id = "m3u", role = "user"),
                parts = listOf(EnginePart(id = "c1", type = "compaction")),
            ),
            EngineMessage(
                EngineMessageInfo(id = "m4a", role = "assistant", summary = true, tokens = EngineTokens(input = 902_000, output = 8_000)),
                parts = listOf(EnginePart(id = "s1", type = "text", text = "Résumé de la conversation…")),
            ),
        )
        val state = engineHistoryEvents(messages).fold(MissionChatState()) { s, e -> s.reduce(e) }
        val compaction = (state.turns[1] as ChatTurn.User).parts.single() as ChatPart.Compaction
        assertEquals(902_000L, compaction.contextBefore)
        assertEquals(8_000L, compaction.contextAfter)
        assertEquals(894_000L, compaction.savedTokens)
        val summary = state.turns[2] as ChatTurn.Assistant
        assertTrue(summary.summary)
        assertTrue(summary.parts.isEmpty())
        // Le contexte repart de la taille du résumé, pas de tout ce que le résumé a lu.
        assertEquals(8_000L, state.contextTokens)
    }

    @Test
    fun `un compactage en direct garde la session occupee sans afficher le resume`() {
        var state = MissionChatState()
            .reduce(EngineStreamEvent.MessageStarted("m2a", "assistant", tokens = EngineTokens(input = 500_000)))
            .reduce(started("m3u", "user"))
            .reduce(part("m3u", "c1", EnginePartSnapshot(type = "compaction")))
            .reduce(EngineStreamEvent.MessageStarted("m4a", "assistant", tokens = EngineTokens(), summary = true))
            .reduce(delta("m4a", "s1", "Résumé"))
        assertTrue(state.streaming)
        assertTrue((state.turns.last() as ChatTurn.Assistant).parts.isEmpty())
        val pending = (state.turns[1] as ChatTurn.User).parts.single() as ChatPart.Compaction
        assertEquals(500_000L, pending.contextBefore)
        assertEquals(null, pending.savedTokens)
        state = state.reduce(EngineStreamEvent.MessageStarted("m4a", "assistant", tokens = EngineTokens(input = 500_000, output = 6_000), summary = true))
        assertEquals(494_000L, ((state.turns[1] as ChatTurn.User).parts.single() as ChatPart.Compaction).savedTokens)
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
    fun aFileATookHandedBackShowsAfterItsFold() {
        // 19/10/2026: two invoice pages rendered by `fichiers_apercu_page`, « au-dessus de ce
        // message » said the agent, folded away with the call where nobody could see them.
        val page = ChatPart.Attachment("t1#0", "image/png", "data:image/png;base64,AAAA", null)
        val rendered = ChatPart.Tool("t1", "fichiers_apercu_page", ToolState.OK, attachments = listOf(page))
        val blocks = listOf(tool("t0"), rendered, tool("t2"), text("p1", "Voici la facture.")).asBlocks()

        assertEquals(listOf("t0", "t1"), assertIs<ChatBlock.Activity>(blocks[0]).parts.map { it.id })
        assertEquals(page, assertIs<ChatBlock.Media>(blocks[1]).part)
        assertEquals(listOf("t2"), assertIs<ChatBlock.Activity>(blocks[2]).parts.map { it.id })
        assertIs<ChatBlock.Prose>(blocks[3])
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
    fun theGatewaysPlaceholderIsNotAParagraph() {
        // 20/10/2026: one such line between every two tool calls of a long browsing turn.
        val placeholder = "[System: Empty message content sanitised to satisfy protocol]"
        val blocks = listOf(tool("t1"), text("p1", placeholder), tool("t2"), text("p2", "$placeholder\n\nDarty bloque ce chemin.")).asBlocks()

        assertEquals(2, blocks.size)
        assertEquals(listOf("t1", "t2"), (blocks.first() as ChatBlock.Activity).parts.map { it.id })
        assertEquals("Darty bloque ce chemin.", (blocks.last() as ChatBlock.Prose).part.text)
    }

    @Test
    fun everySanitisedPlaceholderOfTheGatewayGoes() {
        // 21/10/2026: « [System: Empty name sanitised] » after the message one.
        val blocks = listOf(tool("t1"), text("p1", "[System: Empty name sanitised]"), tool("t2")).asBlocks()

        assertEquals(1, blocks.size)
        assertEquals(listOf("t1", "t2"), (blocks.single() as ChatBlock.Activity).parts.map { it.id })
        assertEquals("[System: autre chose] reste", "[System: autre chose] reste".withoutSanitisedPlaceholder())
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
            ChatPart.Attachment("m2a", "image/jpeg", "data:image/jpeg;base64,AAAA", null),
            ChatPart.Text("x1", "voila"),
        ).asBlocks()

        assertEquals(3, blocks.size)
        assertIs<ChatBlock.Activity>(blocks[0])
        assertIs<ChatBlock.Media>(blocks[1])
        assertIs<ChatBlock.Prose>(blocks[2])
    }

    @Test
    fun consecutiveAssistantStepsAreDrawnAsOneTurnWithOneGrowingActivityLine() {
        // One engine message per step: seven lines for one piece of work until 02/10/2026.
        val turns = listOf(
            ChatTurn.User("m1u", listOf(text("q", "Ajoute le film"))),
            ChatTurn.Assistant("m2a", listOf(thinking("r1"), tool("t1"))),
            ChatTurn.Assistant("m4a", listOf(tool("t2"), tool("t3"))),
            ChatTurn.Assistant("m5a", listOf(tool("t4", ToolState.FAILED))),
            ChatTurn.User("m3u", listOf(text("q2", "Non, celui de 2017"))),
            ChatTurn.Assistant("m6a", listOf(text("p1", "Compris."), tool("t5"))),
        ).mergedAssistantRuns()

        assertEquals(listOf("m1u", "m2a", "m3u", "m6a"), turns.map { it.key })
        val blocks = (turns[1] as ChatTurn.Assistant).parts.asBlocks()
        val activity = assertIs<ChatBlock.Activity>(blocks.single())
        assertEquals(4, activity.toolCount())
        assertTrue(activity.hasFailure())
    }

}
