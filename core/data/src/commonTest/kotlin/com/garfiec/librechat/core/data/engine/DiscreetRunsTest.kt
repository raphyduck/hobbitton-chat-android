package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.scheduler.SchedulerRun
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A discreet mission's runs stay out of the conversation lists, a failure aside (19/10/2026). */
class DiscreetRunsTest {
    private val discreet = setOf("traiter-les-non-lus")

    @Test
    fun `a discreet mission's run is hidden, even when its own run fell out of the window`() {
        val run = SchedulerRun(session = "ses_1", mission = "traiter-les-non-lus", succeeded = true, discreet = true)
        assertTrue(runHidden("traiter-les-non-lus", run, discreet))
        assertTrue(runHidden("traiter-les-non-lus", null, discreet))
    }

    @Test
    fun `a failed or interrupted run shows, and so does every other mission's`() {
        val failed = SchedulerRun(mission = "traiter-les-non-lus", succeeded = false, discreet = true)
        val cut = SchedulerRun(mission = "traiter-les-non-lus", succeeded = true, incomplete = true, discreet = true)
        assertFalse(runHidden("traiter-les-non-lus", failed, discreet))
        assertFalse(runHidden("traiter-les-non-lus", cut, discreet))
        assertFalse(runHidden("brief-crypto", SchedulerRun(mission = "brief-crypto"), discreet))
        assertFalse(runHidden(null, null, discreet))
    }
}
