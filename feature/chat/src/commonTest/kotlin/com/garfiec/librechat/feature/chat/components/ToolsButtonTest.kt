package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.common.ToolConstants
import kotlin.test.Test
import kotlin.test.assertEquals

/** The « + » badge's count, and the order of the sheet it opens. */
class ToolsButtonTest {

    @Test
    fun countsToggleableToolsAndOfferedMcpServers() {
        // The screenshot of 23/09/2026: File Search, memoire, planificateur.
        val count = activeToolCount(
            enabledTools = setOf(ToolConstants.FILE_SEARCH),
            selectedMcpServerNames = setOf("memoire", "planificateur"),
            offeredMcpServerNames = setOf("memoire", "planificateur", "calendrier"),
            showEphemeralTools = true,
        )

        assertEquals(3, count)
    }

    @Test
    fun countsNothingOnTheAgentsEndpoint() {
        // A saved agent runs its own tools; the selections are kept but not sent, so not counted.
        val count = activeToolCount(
            enabledTools = setOf(ToolConstants.WEB_SEARCH, ToolConstants.CODE_INTERPRETER),
            selectedMcpServerNames = setOf("memoire"),
            offeredMcpServerNames = setOf("memoire"),
            showEphemeralTools = false,
        )

        assertEquals(0, count)
    }

    @Test
    fun ignoresAnMcpSelectionWhoseServerIsNoLongerOffered() {
        val count = activeToolCount(
            enabledTools = emptySet(),
            selectedMcpServerNames = setOf("memoire", "ancien-serveur"),
            offeredMcpServerNames = setOf("memoire"),
            showEphemeralTools = true,
        )

        assertEquals(1, count)
    }

    @Test
    fun ignoresKeysTheSheetCannotToggle() {
        val count = activeToolCount(
            enabledTools = setOf(ToolConstants.WEB_SEARCH, "artifacts"),
            selectedMcpServerNames = emptySet(),
            offeredMcpServerNames = emptySet(),
            showEphemeralTools = true,
        )

        assertEquals(1, count)
    }

    private val rows = listOf(
        ToolConstants.WEB_SEARCH,
        ToolConstants.URL_CONTEXT,
        ToolConstants.CODE_INTERPRETER,
        ToolConstants.FILE_SEARCH,
        ToolConstants.MEMORY,
    )

    @Test
    fun pinnedToolsLeadInTheServersOrder() {
        val ordered = pinnedFirst(rows, pinned = listOf(ToolConstants.MEMORY, ToolConstants.FILE_SEARCH))

        assertEquals(
            listOf(
                ToolConstants.MEMORY,
                ToolConstants.FILE_SEARCH,
                ToolConstants.WEB_SEARCH,
                ToolConstants.URL_CONTEXT,
                ToolConstants.CODE_INTERPRETER,
            ),
            ordered,
        )
    }

    @Test
    fun withNothingPinnedTheOrderIsUnchanged() {
        assertEquals(rows, pinnedFirst(rows, pinned = emptyList()))
    }

    @Test
    fun pinnedKeysThatAreNotRowsAreIgnoredAndNothingIsDuplicated() {
        val ordered = pinnedFirst(rows, pinned = listOf("artifacts", ToolConstants.WEB_SEARCH, ToolConstants.WEB_SEARCH))

        assertEquals(rows, ordered)
    }
}
