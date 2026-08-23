package com.garfiec.librechat.core.data.scheduler

import com.garfiec.librechat.core.data.engine.EngineSettingsStore
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

    /** Starts a mission now. Returns what the scheduler said — including its refusals. */
    suspend fun run(name: String): String = api.run(name)

    suspend fun setEnabled(name: String, enabled: Boolean): String =
        if (enabled) api.enable(name) else api.disable(name)
}
