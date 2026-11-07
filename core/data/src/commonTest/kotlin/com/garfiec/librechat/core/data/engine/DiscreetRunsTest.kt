package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.scheduler.SchedulerRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A discreet mission's runs stay out of the conversation lists, failed or not (20/10/2026). */
class DiscreetRunsTest {
    private val discreet = setOf("traiter-les-non-lus")

    @Test
    fun `a discreet mission's run is hidden, failed or not`() {
        assertTrue(runHidden("traiter-les-non-lus", discreet))
    }

    @Test
    fun `every other mission's run and every conversation shows`() {
        assertFalse(runHidden("brief-crypto", discreet))
        assertFalse(runHidden(null, discreet))
    }

    @Test
    fun `a run is unread when it has something to say, a conversation whenever it moved`() {
        assertTrue(runUnread("brief-crypto", SchedulerRun(mission = "brief-crypto", succeeded = false)))
        assertFalse(runUnread("brief-crypto", SchedulerRun(mission = "brief-crypto", hasNews = false)))
        assertTrue(runUnread(null, null))
    }

    /** 07/11/2026: a session paused on the spending ceiling leads the Conversations page. */
    @Test
    fun `a paused session comes first, then the unread, then by activity`() {
        val chats = listOf(
            EngineChatSummary("read", "Lue", 30, running = false),
            EngineChatSummary("unread", "Non lue", 10, running = false, unread = true),
            EngineChatSummary("paused", "En pause", 1, running = false, paused = true),
            EngineChatSummary("recent", "Récente", 40, running = false),
        )
        assertEquals(listOf("paused", "unread", "recent", "read"), inboxOrder(chats).map { it.sessionId })
    }

    /**
     * 06/11/2026: a conversation the scheduler opened under a run's title and plays a relance in
     * stayed read whatever was written in it, and out of the drawer.
     */
    @Test
    fun `a run played inside a conversation leaves it a conversation, whatever its title`() {
        val title = "relance-winovatio-negociation — manuel (lancement)"
        val inside = SchedulerRun(mission = "relance-winovatio-negociation", hasNews = false, inConversation = true)
        assertEquals("relance-winovatio-negociation", missionOf(title, null))
        assertEquals("relance-winovatio-negociation", missionOf(title, SchedulerRun(mission = "relance-winovatio-negociation")))
        assertNull(missionOf(title, inside))
        assertNull(missionOf(title, null, recorded = EngineSessionKind.CHAT))
        assertTrue(runUnread(missionOf(title, inside), null))
        assertEquals("Relance winovatio negociation", listedTitle("ses_1", title, null))
        assertEquals("Relance winovatio negociation", listedTitle("ses_1", title, "relance-winovatio-negociation"))
        assertEquals("Fuite de la Suite", listedTitle("ses_1", "Fuite de la Suite", null))
        assertEquals("ses_1", listedTitle("ses_1", " ", null))
    }
}
