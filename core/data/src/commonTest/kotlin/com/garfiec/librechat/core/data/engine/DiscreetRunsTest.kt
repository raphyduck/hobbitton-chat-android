package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.scheduler.SchedulerRun
import kotlin.test.Test
import kotlin.test.assertFalse
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
}
