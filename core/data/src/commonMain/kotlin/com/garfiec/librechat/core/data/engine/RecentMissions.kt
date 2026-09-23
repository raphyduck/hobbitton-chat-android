package com.garfiec.librechat.core.data.engine

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.onApiDispatcher
import com.garfiec.librechat.core.data.scheduler.SchedulerRepository
import com.garfiec.librechat.core.model.engine.EngineSession
import com.garfiec.librechat.core.model.engine.EngineSessionStatus
import com.garfiec.librechat.core.model.scheduler.ScheduledMission
import com.garfiec.librechat.core.network.api.AgentEngineApi

/**
 * What a mission row in the drawer's recents says about itself, beyond its title.
 *
 * [Settled] is deliberately not « done ». Deciding that a finished session actually succeeded costs
 * its whole transcript (`judgeMission`), and the drawer lists every session the person launched —
 * paying that per row on every opening of a sidebar is the N+1 the Tasks tab can afford and a
 * sidebar cannot. The row therefore claims only what the status map proves; the conversation says
 * the rest once opened.
 */
enum class RecentMissionStatus { Running, Settled, Failed, Incomplete }

/** One engine session as the drawer's recents list shows it, next to the chats. */
data class RecentMission(
    val sessionId: String,
    val title: String,
    val lastActivityMillis: Long?,
    val status: RecentMissionStatus,
)

/** Feeds the drawer's recents with missions. Bound only where the engine's graph exists (D-034). */
interface RecentMissionsSource {
    suspend fun recentMissions(): List<RecentMission>
}

/**
 * The missions worth a line among the chats — rule « B », chosen by Raphaël on 23/09/2026.
 *
 *  * every session **the person launched** is listed, like a chat they started ;
 *  * a **scheduled** run is listed only when its mission's **latest** run needs attention: failed,
 *    or stopped short of the end.
 *
 * The second rule is the whole point. The scheduler starts about nine sessions a day; listed as they
 * come, they would bury every chat within one morning. And it is the *latest* run that decides, not
 * any failed one: on 23/09/2026 the gateway budget refused about forty attempts in two hours, and
 * each was a session. Once the mission ran again and succeeded, none of those forty had anything left
 * to say — listing them would have been forty lines of noise about a problem already solved.
 *
 * Pure, and therefore the part the tests hold.
 */
fun selectRecentMissions(
    sessions: List<EngineSession>,
    statuses: Map<String, EngineSessionStatus>,
    scheduled: List<ScheduledMission>,
): List<RecentMission> {
    val latestRuns = scheduled.mapNotNull { mission -> mission.lastRun?.sessionId?.let { it to mission } }.toMap()

    return sessions.mapNotNull { session ->
        val title = session.title.orEmpty().ifBlank { session.id }
        val status = if (isScheduledRun(title)) {
            // A scheduled run earns a line only as its mission's latest run, and only in trouble.
            val run = latestRuns[session.id]?.lastRun ?: return@mapNotNull null
            when {
                run.incomplete -> RecentMissionStatus.Incomplete
                run.succeeded == false -> RecentMissionStatus.Failed
                // Null while running, true once done: neither is anybody's problem yet.
                else -> return@mapNotNull null
            }
        } else {
            val active = statuses[session.id]
            if (active != null && active.type != IDLE) RecentMissionStatus.Running else RecentMissionStatus.Settled
        }
        RecentMission(
            sessionId = session.id,
            title = title,
            lastActivityMillis = session.time?.updated ?: session.time?.created,
            status = status,
        )
    }.sortedByDescending { it.lastActivityMillis ?: Long.MIN_VALUE }
}

/**
 * Whether [title] is one the scheduler wrote: `<mission> — <trigger>`, where the trigger is the due
 * minute (`2026-09-23T06:30+0200`) or `manuel`, optionally followed by ` reprise N`.
 *
 * Read from the title's **shape**, not by matching a mission name the scheduler still lists. A
 * deleted mission leaves its sessions behind on the engine, and a name-based match would suddenly
 * reclassify every one of them as launched by hand — the very flood this list exists to prevent,
 * released by a delete. The format is the server's (`boucle.py`, `titre=f"{mission.nom} —
 * {etiquette}"`); a session launched from the app is titled with its objective and never ends this
 * way.
 */
internal fun isScheduledRun(title: String): Boolean {
    val separator = title.lastIndexOf(SCHEDULER_SEPARATOR)
    if (separator <= 0) return false
    return SCHEDULER_TRIGGER.matches(title.substring(separator + SCHEDULER_SEPARATOR.length))
}

private const val IDLE = "idle"
private const val SCHEDULER_SEPARATOR = " — "
private val SCHEDULER_TRIGGER =
    Regex("""(manuel|\d{4}-\d{2}-\d{2}T\d{2}:\d{2}[+-]\d{4})( reprise \d+)?""")

/**
 * Three round trips per refresh, whatever the number of sessions: the session list, the status map,
 * and the scheduler's state. No transcript is read (see [RecentMissionStatus.Settled]).
 *
 * Never throws. The drawer is the app's spine, and an engine that is down, unconfigured or signed
 * out must cost the drawer its mission rows — not the drawer. An empty list is the answer then, and
 * the reason goes to the log.
 */
class EngineRecentMissionsSource(
    private val api: AgentEngineApi,
    private val scheduler: SchedulerRepository,
    private val settings: EngineSettingsStore,
) : RecentMissionsSource {

    override suspend fun recentMissions(): List<RecentMission> {
        if (settings.access() == null) return emptyList()
        // Off the caller's dispatcher: this is called from the drawer's viewModelScope, and decoding
        // a few hundred sessions there would run on the UI thread (this module's rule, CLAUDE.md).
        return runCatching {
            onApiDispatcher {
                // The scheduler is optional: without it, scheduled runs simply never qualify.
                val scheduled = runCatching { scheduler.missions() }.getOrDefault(emptyList())
                selectRecentMissions(api.sessions(), api.status(), scheduled)
            }
        }.onFailure { Logger.w(it) { "Drawer: engine sessions unavailable" } }
            .getOrDefault(emptyList())
    }
}
