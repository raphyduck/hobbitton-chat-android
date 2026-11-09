package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineFileAttachment
import com.garfiec.librechat.core.model.engine.EnginePartSnapshot
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * 09/11/2026, Raphaël's capture : « les trois remises sont passées, sans erreur », and nothing on
 * screen. The navigateur, a sub-agent, had passed three documents to the conversation from its own
 * session ; the engine's `task` returned its text alone. The files a delegation's session handed back
 * are read once and grafted onto the `task` part, where the fold draws them like any tool's files.
 */
class MissionChatDelegationTest {

    private val taskOutput = "<task id=\"ses_fille\" state=\"completed\">\n<task_result>\nTrois remises faites.\n</task_result>\n</task>"

    private fun task(status: String?, output: String? = null, input: JsonElement? = null) =
        EnginePartSnapshot(
            type = "tool",
            tool = "task",
            callId = "call_1",
            status = status,
            input = input ?: buildJsonObject { put("subagent_type", JsonPrimitive("navigateur")) },
            output = output,
        )

    @Test
    fun `la session fille d'une delegation se lit dans la sortie de task, ou dans son task_id`() {
        assertEquals("ses_fille", delegationOf("task", null, taskOutput))
        val resumed = buildJsonObject { put("task_id", JsonPrimitive("ses_reprise")) }
        assertEquals("ses_reprise", delegationOf("task", resumed, null))
        // The output wins over the argument : it names the session the call really ran in.
        assertEquals("ses_fille", delegationOf("task", resumed, taskOutput))
        assertNull(delegationOf("memoire_lire", null, "<task id=\"ses_x\">"))
        assertNull(delegationOf("task", null, "running…"))
    }

    @Test
    fun `une delegation terminee est a lire, une fois, et ses fichiers se greffent sur la part task`() {
        var state = missionChatFrom(
            listOf(
                EngineStreamEvent.MessageStarted("msg_a", "assistant"),
                EngineStreamEvent.PartUpdated("msg_a", "t1", task(status = null)),
            ),
        )
        // Still running : nothing to read, the session is not even named yet.
        assertEquals(emptySet(), state.delegationsToRead())

        state = state.reduce(EngineStreamEvent.PartUpdated("msg_a", "t1", task("completed", taskOutput)))
        assertEquals(setOf("ses_fille"), state.delegationsToRead())

        val plan = ChatPart.Attachment("ses_fille#p9#0", "application/pdf", "data:application/pdf;base64,AAAA", "Plan V4.pdf")
        state = state.withDelegatedFiles("ses_fille", listOf(plan))
        assertEquals(emptySet(), state.delegationsToRead())
        val part = assertIs<ChatPart.Tool>(assertIs<ChatTurn.Assistant>(state.turns.single()).parts.single())
        assertEquals(listOf(plan), part.attachments)
        assertEquals("ses_fille", part.delegation)

        // A later snapshot of the same part (the engine rewrites it) keeps the grafted files.
        state = state.reduce(EngineStreamEvent.PartUpdated("msg_a", "t1", task("completed", taskOutput)))
        val again = assertIs<ChatPart.Tool>(assertIs<ChatTurn.Assistant>(state.turns.single()).parts.single())
        assertEquals(listOf(plan), again.attachments)

        // Drawn after the fold, as any tool's file.
        val blocks = assertIs<ChatTurn.Assistant>(state.turns.single()).parts.asBlocks()
        assertIs<ChatBlock.Activity>(blocks[0])
        assertEquals(plan, assertIs<ChatBlock.Media>(blocks[1]).part)
    }

    @Test
    fun `les fichiers d'une session fille sont ceux de ses outils, avec des identifiants a elle`() {
        val events = listOf(
            EngineStreamEvent.MessageStarted("msg_f", "assistant"),
            EngineStreamEvent.PartUpdated("msg_f", "r1", EnginePartSnapshot(type = "reasoning", text = "je remets")),
            EngineStreamEvent.PartUpdated(
                "msg_f",
                "p9",
                EnginePartSnapshot(
                    type = "tool",
                    tool = "planificateur_annuaire_appeler",
                    status = "completed",
                    output = "{\"remis\": \"Plan V4.pdf\"}",
                    attachments = listOf(
                        EngineFileAttachment("application/pdf", "data:application/pdf;base64,AAAA", "Plan V4.pdf"),
                        EngineFileAttachment("image/png", null, "sans octets"),
                    ),
                ),
            ),
            EngineStreamEvent.PartUpdated("msg_f", "t2", EnginePartSnapshot(type = "text", text = "Fait.")),
        )
        val files = delegatedFilesOf("ses_fille", events)
        assertEquals(1, files.size)
        assertEquals("ses_fille#p9#0", files[0].id)
        assertEquals("Plan V4.pdf", files[0].filename)
        assertEquals("application/pdf", files[0].mime)
    }

    @Test
    fun `un fichier rendu par un outil porte son nom seul, sans l'adresse de la ressource`() {
        // 09/11/2026 : le moteur nomme le fichier par l'URI de la ressource, « file:///1 - Plan V4.pdf ».
        assertEquals("1 - Plan V4.pdf", shownFileName("file:///1 - Plan V4.pdf"))
        assertEquals("devis.pdf", shownFileName("devis.pdf"))
        assertNull(shownFileName(null))
        val events = listOf(
            EngineStreamEvent.PartUpdated(
                "msg_f",
                "p1",
                EnginePartSnapshot(
                    type = "tool",
                    tool = "planificateur_annuaire_appeler",
                    status = "completed",
                    attachments = listOf(
                        EngineFileAttachment("application/pdf", "data:application/pdf;base64,AAAA", "file:///2 - Dossier.pdf"),
                    ),
                ),
            ),
        )
        assertEquals("2 - Dossier.pdf", delegatedFilesOf("ses_x", events).single().filename)
    }
}
