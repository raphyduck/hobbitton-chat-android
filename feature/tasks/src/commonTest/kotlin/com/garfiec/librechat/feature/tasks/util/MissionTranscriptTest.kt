package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineMessage
import com.garfiec.librechat.core.model.engine.EngineMessageInfo
import com.garfiec.librechat.core.model.engine.EnginePart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissionTranscriptTest {

    private fun message(role: String, vararg parts: EnginePart) =
        EngineMessage(info = EngineMessageInfo(id = "m", role = role), parts = parts.toList())

    private fun text(value: String?) = EnginePart(type = "text", text = value)
    private fun tool(name: String) = EnginePart(type = "tool", tool = name)

    @Test
    fun textAndToolPartsBecomeEntriesInOrderCarryingTheirRole() {
        val transcript = missionTranscript(
            listOf(
                message("user", text("Relève les transactions Qonto.")),
                message("assistant", tool("qonto_list_transactions"), text("Trois sans justificatif.")),
            ),
        )

        assertEquals(3, transcript.size)
        assertEquals(TranscriptEntry("user", TranscriptEntry.Kind.TEXT, "Relève les transactions Qonto."), transcript[0])
        assertEquals(TranscriptEntry("assistant", TranscriptEntry.Kind.TOOL, "qonto_list_transactions"), transcript[1])
        assertEquals(TranscriptEntry("assistant", TranscriptEntry.Kind.TEXT, "Trois sans justificatif."), transcript[2])
    }

    @Test
    fun blankAndUnknownPartsAreDropped() {
        // A tool-only assistant turn carries an empty text part beside the call, and the run's own
        // step bookkeeping (`step-start`) is not something a person reads — neither should render.
        val transcript = missionTranscript(
            listOf(
                message(
                    "assistant",
                    text("   "),
                    EnginePart(type = "step-start"),
                    tool("memoire_ecrire"),
                ),
            ),
        )

        assertEquals(1, transcript.size)
        assertEquals(TranscriptEntry.Kind.TOOL, transcript.single().kind)
        assertEquals("memoire_ecrire", transcript.single().text)
    }

    @Test
    fun aToolPartWithoutANameFallsBackToItsTextRatherThanVanishing() {
        val transcript = missionTranscript(
            listOf(message("assistant", EnginePart(type = "tool", tool = null, text = "read"))),
        )
        assertEquals(listOf(TranscriptEntry("assistant", TranscriptEntry.Kind.TOOL, "read")), transcript)
    }

    @Test
    fun aRunThatNeverSpokeYieldsNoLines() {
        val transcript = missionTranscript(
            listOf(message("assistant", EnginePart(type = "step-start"), text(null))),
        )
        assertTrue(transcript.isEmpty())
    }
}
