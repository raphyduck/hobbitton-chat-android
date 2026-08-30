package com.garfiec.librechat.core.data.scheduler

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.model.scheduler.Consumption
import com.garfiec.librechat.core.model.scheduler.ProviderHealth
import com.garfiec.librechat.core.model.scheduler.ScheduledMission
import com.garfiec.librechat.core.network.api.SchedulerApi

/**
 * The recurring missions, as the Tasks tab needs them.
 *
 * Thin on purpose: the scheduler is the source of truth, nothing is cached locally, and a mission
 * that exists only on a phone is a mission nobody can supervise — the same rule the engine's
 * repository follows.
 *
 * Every method answers null-or-empty when the scheduler has not been configured, rather than
 * throwing. Not having a scheduler is a normal state for a fresh install, not an error to show in
 * red; the screen says « not configured » and offers the settings form.
 */
class SchedulerRepository(
    private val api: SchedulerApi,
    private val settings: EngineSettingsStore,
) {

    suspend fun isConfigured(): Boolean = settings.access()?.hasScheduler == true

    /**
     * Every mission, newest schedule first — running ones at the top.
     *
     * A mission that is working right now is the one thing a person opens this screen to see, so
     * it leads. After that, the order is the scheduler's own, which is the file's order and
     * therefore stable between refreshes.
     */
    suspend fun missions(): List<ScheduledMission> {
        if (!isConfigured()) return emptyList()
        return api.state().missions.sortedByDescending { it.running }
    }

    /**
     * What the platform spent over the last [days] days, or null when no scheduler is configured.
     *
     * Null rather than an empty report, and the distinction matters on this screen more than most:
     * an empty report renders as « nothing spent », which on an unconfigured install is a lie about
     * money. The screen says « not configured » instead and offers the settings form.
     */
    suspend fun consumption(days: Int = 7): Consumption? {
        if (!isConfigured()) return null
        return api.consumption(days)
    }

    /**
     * Which providers answer, or null when no scheduler is configured.
     *
     * **Costs a real call to every model.** Never call this from a refresh path — it belongs to a
     * button the person pressed, and the repository deliberately offers no cached variant that
     * would make it look free.
     */
    suspend fun providers(): ProviderHealth? {
        if (!isConfigured()) return null
        return api.providers()
    }

    /** Starts a mission now. Returns what the scheduler said — including its refusals. */
    /**
     * Runs a scheduler action, swallowing its failure into a log line.
     *
     * The tab's list is the screen's truth and it is refreshed right after; a failed action shows
     * as « nothing changed », which is what happened. Turning it into the tab's red banner would
     * report the platform unreachable on a screen whose list had just loaded fine.
     */
    suspend fun runCatchingAction(
        name: String,
        what: String,
        action: suspend SchedulerRepository.() -> String,
    ) {
        runCatching { action() }
            .onFailure { failure -> Logger.w(failure, tag = "Scheduler") { "Could not $what $name" } }
    }

    /** Changes named fields of a scheduled mission; what is not named is not touched. */
    suspend fun updateMission(
        name: String,
        cron: String? = null,
        runAt: String? = null,
        model: String? = null,
        connectors: List<String>? = null,
    ): String = api.updateMission(
        name = name, cron = cron, runAt = runAt, model = model, connectors = connectors,
    )

    /** Deletes a scheduled mission. Its run history is kept server-side. */
    suspend fun deleteMission(name: String): String = api.deleteMission(name)

    suspend fun run(name: String): String = api.run(name)

    suspend fun setEnabled(name: String, enabled: Boolean): String =
        if (enabled) api.enable(name) else api.disable(name)
}
