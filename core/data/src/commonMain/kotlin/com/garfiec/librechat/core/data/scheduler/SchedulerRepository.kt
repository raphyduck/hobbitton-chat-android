package com.garfiec.librechat.core.data.scheduler

import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.pricing.ModelPriceSource
import com.garfiec.librechat.core.model.scheduler.ChatBudget
import com.garfiec.librechat.core.model.scheduler.Consumption
import com.garfiec.librechat.core.model.scheduler.ModelPrices
import com.garfiec.librechat.core.model.scheduler.ModelTierChoice
import com.garfiec.librechat.core.model.scheduler.ModelTiers
import com.garfiec.librechat.core.model.scheduler.PortalIdentity
import com.garfiec.librechat.core.model.scheduler.ProviderHealth
import com.garfiec.librechat.core.model.scheduler.PushChannel
import com.garfiec.librechat.core.model.scheduler.ScheduledMission
import com.garfiec.librechat.core.network.api.SchedulerApi
import kotlin.time.Clock

/**
 * The recurring missions, as the Tasks tab needs them.
 *
 * Thin on purpose: the scheduler is the source of truth, nothing is cached locally but two answers
 * that do not change while a phone is open (who is signed in, the ladder of tiers; 26/10/2026
 * evening, each conversation opened asked both again), and a mission that exists only on a phone
 * is a mission nobody can supervise — the same rule the engine's repository follows.
 *
 * Every method answers null-or-empty when the scheduler has not been configured, rather than
 * throwing. Not having a scheduler is a normal state for a fresh install, not an error to show in
 * red; the screen says « not configured » and offers the settings form.
 */
class SchedulerRepository(
    private val api: SchedulerApi,
    private val settings: EngineSettingsStore,
) : ModelPriceSource, ChatBudgetSource {

    suspend fun isConfigured(): Boolean = settings.access()?.hasScheduler == true

    /**
     * Who the portal says is signed in, for the greeting of a new conversation (10/10/2026). Null
     * when the scheduler is not configured, predates the route, or cannot be reached: a greeting
     * without a name is the right answer to all three.
     */
    suspend fun identity(): PortalIdentity? {
        if (!isConfigured()) return null
        identityRead?.takeIf { now() - it.first < IDENTITY_TTL_MS }?.let { return it.second }
        return runCatching { api.identity() }.getOrNull()?.also { identityRead = now() to it }
    }

    private var identityRead: Pair<Long, PortalIdentity>? = null
    private var tiersRead: Pair<Long, ModelTiers>? = null

    /** Sign-out: the next person signed in may be another. */
    fun forget() {
        identityRead = null
        tiersRead = null
    }

    /**
     * Where the server publishes its push notifications (19/10/2026), or null: no scheduler, a
     * scheduler without the route or without ntfy, or one that cannot be reached right now.
     */
    suspend fun pushChannel(): PushChannel? {
        if (!isConfigured()) return null
        return runCatching { api.pushChannel() }.getOrNull()?.takeIf { it.usable }
    }

    /**
     * The tiers of model the server offers a conversation (25/10/2026), or null: no scheduler, a
     * scheduler without the route, or one that cannot be reached. Null leaves the conversation
     * without a « redo stronger »; nothing else depends on it.
     */
    suspend fun modelTiers(): ModelTiers? {
        if (!isConfigured()) return null
        tiersRead?.takeIf { now() - it.first < TIERS_TTL_MS }?.let { return it.second }
        return runCatching { api.modelTiers() }.getOrNull()?.also { tiersRead = now() to it }
    }

    /**
     * The tier and model a first message calls for, from the server's trieur (25/10/2026); null
     * when there is no scheduler, no route, no trieur, or no answer. Null means « the default »,
     * never an error to show: the conversation starts either way.
     */
    suspend fun triage(text: String, hasImages: Boolean): ModelTierChoice? {
        if (!isConfigured()) return null
        return runCatching { api.triageModel(text, hasImages) }.getOrNull()
    }

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

    override suspend fun chatBudget(): ChatBudget? {
        if (!isConfigured()) return null
        return api.chatBudget()
    }

    override suspend fun setChatBudget(day: Double, week: Double, month: Double): ChatBudget =
        api.setChatBudget(day = day, week = week, month = month)

    /** Changes named fields of a scheduled mission; what is not named is not touched. */
    suspend fun updateMission(name: String, cron: String?, runAt: String?): String =
        api.updateMission(name = name, cron = cron, runAt = runAt)

    /** Keeps a mission's runs out of the conversation lists, or brings them back (19/10/2026). */
    suspend fun setDiscreet(name: String, discreet: Boolean): String =
        api.updateMission(name = name, discreet = discreet)

    /** Deletes a scheduled mission. Its run history is kept server-side. */
    suspend fun deleteMission(name: String): String = api.deleteMission(name)

    /** Starts a mission now. Returns what the scheduler said — including its refusals. */
    suspend fun run(name: String): String = api.run(name)

    suspend fun setEnabled(name: String, enabled: Boolean): String =
        if (enabled) api.enable(name) else api.disable(name)

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        /** An hour: the name on the drawer's foot and in the greeting. */
        const val IDENTITY_TTL_MS = 60L * 60 * 1000

        /** Ten minutes: the ladder changes when the platform is reconfigured. */
        const val TIERS_TTL_MS = 10L * 60 * 1000
    }
}
