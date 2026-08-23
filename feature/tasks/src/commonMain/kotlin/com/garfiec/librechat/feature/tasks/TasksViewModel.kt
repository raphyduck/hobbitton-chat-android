package com.garfiec.librechat.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.engine.Mission
import com.garfiec.librechat.core.data.engine.engineFailureKind
import com.garfiec.librechat.core.data.scheduler.SchedulerRepository
import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.model.scheduler.Consumption
import com.garfiec.librechat.core.model.scheduler.ProviderHealth
import com.garfiec.librechat.core.model.scheduler.ScheduledMission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the Tasks tab shows.
 *
 * [engineConfigured] is separate from [error] on purpose: « you have not set the engine up » and
 * « the engine did not answer » are different problems with different remedies, and collapsing them
 * into one red banner sends the person to check their network when they should be filling in a
 * settings form.
 */
data class TasksUiState(
    val engineConfigured: Boolean = true,
    val loading: Boolean = false,
    val missions: List<Mission> = emptyList(),
    /**
     * The recurring missions, from the scheduler — a different service from the engine, and the
     * only place that knows a mission runs every night. Empty when no scheduler is configured,
     * which is a normal state and not an error.
     */
    val scheduled: List<ScheduledMission> = emptyList(),
    val schedulerConfigured: Boolean = false,
    /**
     * What the platform spent, by model, over the last week. Null while unknown — either the
     * scheduler is not configured, or its own gateway did not answer. Null is rendered as nothing
     * at all rather than as zero: a « 0,00 $ » on a screen about money is a claim, and one that
     * would be false here.
     */
    val consumption: Consumption? = null,
    /**
     * Which providers answer — null until someone asks, and deliberately so.
     *
     * Obtaining it calls every model for real (~0,0015 $, two to three seconds). Loading it with
     * the rest of the screen would spend money on every glance at the tab, for an answer that
     * changes about once a month. It stays null until [checkProviders].
     */
    val providers: ProviderHealth? = null,
    val providersChecking: Boolean = false,
    /** Why the last provider check failed, or null. Kept apart from [error] for the usual reason:
     * a gateway that did not answer is not an engine that did not answer. */
    val providersError: String? = null,
    val profiles: List<String> = emptyList(),
    /** Why the last call failed, or null. The screen turns it into a sentence and an offer. */
    val error: EngineFailureKind? = null,
)

class TasksViewModel(
    private val repository: EngineMissionRepository,
    private val scheduler: SchedulerRepository,
    private val settings: EngineSettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (settings.access() == null) {
                // Not an error: nothing is broken, the engine simply has not been configured. The
                // screen offers the settings form rather than a retry button that cannot help.
                _state.update { it.copy(engineConfigured = false, loading = false, error = null) }
                return@launch
            }
            _state.update { it.copy(engineConfigured = true, loading = true, error = null) }
            refreshScheduled()
            runCatching { repository.missions() to repository.profiles() }
                .onSuccess { (missions, profiles) ->
                    _state.update {
                        it.copy(
                            loading = false,
                            // Newest first: the mission someone just launched is the one they are
                            // looking for, and the engine returns them oldest first.
                            missions = missions.sortedByDescending { mission -> mission.createdAtMillis ?: 0 },
                            profiles = profiles.map { profile -> profile.name },
                        )
                    }
                }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Tasks") { "Could not read the engine's missions" }
                    _state.update { it.copy(loading = false, error = failure.engineFailureKind()) }
                }
        }
    }

    /**
     * The scheduler in its own request, and its failures kept off the engine's error banner.
     *
     * The two services are independent: a scheduler that is down must not empty the sessions list,
     * and an engine that is down must not hide tonight's schedule. Collapsing them into one
     * `runCatching` would make either outage look like both.
     */
    private suspend fun refreshScheduled() {
        runCatching { scheduler.isConfigured() to scheduler.missions() }
            .onSuccess { (configured, missions) ->
                _state.update { it.copy(schedulerConfigured = configured, scheduled = missions) }
            }
            .onFailure { failure ->
                Logger.w(failure, tag = "Tasks") { "Could not read the scheduler" }
                _state.update { it.copy(scheduled = emptyList()) }
            }
        refreshConsumption()
    }

    /**
     * The week's spend, in its own request again — same reasoning one level down.
     *
     * This one reaches further than the others: the scheduler asks the gateway, which may be down
     * while the scheduler is perfectly fine. A failure here must therefore not empty the mission
     * list, so it clears only its own field and says nothing on the banner.
     */
    private suspend fun refreshConsumption() {
        runCatching { scheduler.consumption(days = CONSUMPTION_DAYS) }
            .onSuccess { report -> _state.update { it.copy(consumption = report) } }
            .onFailure { failure ->
                Logger.w(failure, tag = "Tasks") { "Could not read the week's spend" }
                _state.update { it.copy(consumption = null) }
            }
    }

    /**
     * Asks every provider whether it still answers. **Spends money**, so it is only ever called
     * from an explicit press — never from [refresh].
     */
    fun checkProviders() {
        if (_state.value.providersChecking) return
        viewModelScope.launch {
            _state.update { it.copy(providersChecking = true, providersError = null) }
            runCatching { scheduler.providers() }
                .onSuccess { health ->
                    _state.update { it.copy(providers = health, providersChecking = false) }
                }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Tasks") { "Could not check the providers" }
                    _state.update {
                        it.copy(
                            providersChecking = false,
                            // The message, not a generic « failed »: the scheduler forwards the
                            // gateway's own sentence, and that sentence is the answer.
                            providersError = failure.message ?: "…",
                        )
                    }
                }
        }
    }

    /** Starts a scheduled mission now, without waiting for its cron. */
    fun runScheduled(name: String) {
        viewModelScope.launch {
            runCatching { scheduler.run(name) }
                .onFailure { failure -> Logger.w(failure, tag = "Tasks") { "Could not start $name" } }
            refresh()
        }
    }

    /** Suspends a mission, or puts it back — its history survives either way. */
    fun setScheduledEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { scheduler.setEnabled(name, enabled) }
                .onFailure { failure -> Logger.w(failure, tag = "Tasks") { "Could not toggle $name" } }
            refresh()
        }
    }

    fun launch(profile: String, objective: String, connectors: List<String>, autonomous: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.launch(profile, objective, connectors, autonomous = autonomous) }
                .onSuccess { refresh() }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Tasks") { "Could not start the mission" }
                    _state.update { it.copy(loading = false, error = failure.engineFailureKind()) }
                }
        }
    }

    private companion object {
        /** A week: long enough to see a trend, short enough that today still stands out. */
        const val CONSUMPTION_DAYS = 7
    }

    fun abort(sessionId: String) {
        viewModelScope.launch {
            runCatching { repository.abort(sessionId) }
                .onFailure { failure -> Logger.w(failure, tag = "Tasks") { "Could not stop $sessionId" } }
            refresh()
        }
    }
}
