package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EngineSession
import com.garfiec.librechat.core.model.engine.EngineSessionStatus
import com.garfiec.librechat.core.model.engine.EngineTime
import com.garfiec.librechat.core.model.scheduler.MissionRun
import com.garfiec.librechat.core.model.scheduler.ScheduledMission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rule « B » — which missions earn a line among the chats in the drawer.
 *
 * The titles below are the scheduler's real ones, read from the Tasks list on 23/09/2026.
 */
class RecentMissionsTest {

    private fun session(id: String, title: String, updated: Long) =
        EngineSession(id = id, title = title, time = EngineTime(updated = updated))

    private fun mission(name: String, run: MissionRun?) =
        ScheduledMission(name = name, profile = "veille", enabled = true, lastRun = run)

    @Test
    fun `the scheduler's titles are recognised by their shape`() {
        assertTrue(isScheduledRun("brief-crypto — 2026-09-23T06:45+0200"))
        assertTrue(isScheduledRun("suivi-dossiers-ouverts — manuel"))
        assertTrue(isScheduledRun("rapprochement-qonto — 2026-09-23T05:00+0200 reprise 2"))
        assertTrue(isScheduledRun("Aurou élec, sortir d'Engie, jour 1 — 2026-09-14T08:30+0200"))
    }

    @Test
    fun `a mission launched from the app is not mistaken for a scheduled run`() {
        assertFalse(isScheduledRun("Fais le point sur les factures de septembre"))
        // A dash in the objective is not enough: what follows it has to be a trigger.
        assertFalse(isScheduledRun("Relance Engie — facture de clôture"))
        assertFalse(isScheduledRun("— manuel"))
    }

    @Test
    fun `every session launched by hand is listed, newest first`() {
        val recents = selectRecentMissions(
            sessions = listOf(
                session("a", "Vérifie les sauvegardes", updated = 100),
                session("b", "Compare les devis", updated = 300),
            ),
            statuses = emptyMap(),
            scheduled = emptyList(),
        )

        assertEquals(listOf("b", "a"), recents.map { it.sessionId })
        assertTrue(recents.all { it.status == RecentMissionStatus.Settled })
    }

    @Test
    fun `a hand-launched session the engine reports busy is shown running`() {
        val recents = selectRecentMissions(
            sessions = listOf(session("a", "Vérifie les sauvegardes", updated = 100)),
            statuses = mapOf("a" to EngineSessionStatus(type = "busy")),
            scheduled = emptyList(),
        )

        assertEquals(RecentMissionStatus.Running, recents.single().status)
    }

    @Test
    fun `a scheduled run that went well stays out of the list`() {
        val recents = selectRecentMissions(
            sessions = listOf(session("s1", "brief-crypto — 2026-09-23T06:45+0200", updated = 100)),
            statuses = emptyMap(),
            scheduled = listOf(mission("brief-crypto", MissionRun(succeeded = true, sessionId = "s1"))),
        )

        assertTrue(recents.isEmpty())
    }

    @Test
    fun `a failed latest run is listed as failed`() {
        val recents = selectRecentMissions(
            sessions = listOf(session("s1", "brief-crypto — 2026-09-23T06:45+0200", updated = 100)),
            statuses = emptyMap(),
            scheduled = listOf(mission("brief-crypto", MissionRun(succeeded = false, sessionId = "s1"))),
        )

        assertEquals(RecentMissionStatus.Failed, recents.single().status)
    }

    @Test
    fun `a latest run stopped short of the end is listed as incomplete`() {
        // 23/09/2026: suivi-dossiers-ouverts, « INCOMPLET : atterrissage demandé (1922 s sur 2400) ».
        val recents = selectRecentMissions(
            sessions = listOf(session("s1", "suivi-dossiers-ouverts — manuel", updated = 100)),
            statuses = emptyMap(),
            scheduled = listOf(
                mission("suivi-dossiers-ouverts", MissionRun(succeeded = true, incomplete = true, sessionId = "s1")),
            ),
        )

        assertEquals(RecentMissionStatus.Incomplete, recents.single().status)
    }

    @Test
    fun `failures a later success has made moot are not listed`() {
        // The morning of 23/09/2026 in miniature: the budget refused the run twice, then it passed.
        val recents = selectRecentMissions(
            sessions = listOf(
                session("f1", "rapprochement-qonto — 2026-09-23T05:00+0200", updated = 100),
                session("f2", "rapprochement-qonto — 2026-09-23T05:00+0200 reprise 2", updated = 200),
                session("ok", "rapprochement-qonto — manuel", updated = 300),
            ),
            statuses = emptyMap(),
            scheduled = listOf(mission("rapprochement-qonto", MissionRun(succeeded = true, sessionId = "ok"))),
        )

        assertTrue(recents.isEmpty(), "old failures resurfaced: ${recents.map { it.sessionId }}")
    }

    @Test
    fun `a running scheduled run is not anybody's problem yet`() {
        val recents = selectRecentMissions(
            sessions = listOf(session("s1", "brief-crypto — manuel", updated = 100)),
            statuses = mapOf("s1" to EngineSessionStatus(type = "busy")),
            scheduled = listOf(mission("brief-crypto", MissionRun(succeeded = null, sessionId = "s1"))),
        )

        assertTrue(recents.isEmpty())
    }

    @Test
    fun `the runs of a deleted mission do not flood the list`() {
        // The scheduler no longer lists the mission, but the engine still holds its sessions.
        val recents = selectRecentMissions(
            sessions = (1..50).map { session("s$it", "ancienne-veille — 2026-08-${10 + it % 20}T07:00+0200", it.toLong()) },
            statuses = emptyMap(),
            scheduled = emptyList(),
        )

        assertTrue(recents.isEmpty())
    }
}
