package com.garfiec.librechat.core.data.scheduler

import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.pricing.ModelPriceSource
import com.garfiec.librechat.core.model.scheduler.Consumption
import com.garfiec.librechat.core.model.scheduler.ModelPrices
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
) : ModelPriceSource {

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
     * What each model costs, in dollars per million tokens.
     *
     * The opposite of [providers] in every way that matters: no model is called, nothing is spent,
     * and the gateway answers off a table it already holds — so a picker may ask as it opens.
     *
     * An unconfigured install answers an empty table rather than null. There is no lie in it: a
     * missing price is already rendered as words rather than as a zero, so « we have no scheduler »
     * and « the gateway has no price for this one » land on the same honest screen.
     */
    override suspend fun fetch(): ModelPrices {
        if (!isConfigured()) return ModelPrices.NONE
        return api.prices()
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
    /** Changes named fields of a scheduled mission; what is not named is not touched. */
    suspend fun updateMission(name: String, cron: String?, runAt: String?): String =
        api.updateMission(name = name, cron = cron, runAt = runAt)

    /** Deletes a scheduled mission. Its run history is kept server-side. */
    suspend fun deleteMission(name: String): String = api.deleteMission(name)

    suspend fun run(name: String): String = api.run(name)

    suspend fun setEnabled(name: String, enabled: Boolean): String =
        if (enabled) api.enable(name) else api.disable(name)
}
