package com.garfiec.librechat.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.scheduler.SchedulerRepository
import com.garfiec.librechat.core.model.scheduler.Consumption
import com.garfiec.librechat.core.model.scheduler.ProviderHealth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The windows the spend is read over (11/11/2026, Raphaël: « les usages sur 7 jours, mais
 * également sur 24 heures et sur 30 jours »). The gateway counts by calendar day, both bounds
 * included, so the shortest window is today since midnight, not a sliding 24 hours: the label says
 * « Aujourd'hui » rather than promise what the figure is not. The week stays the one shown first.
 */
enum class UsagePeriod(val days: Int) {
    TODAY(1),
    WEEK(7),
    MONTH(30),
}

/**
 * What the Usage screen shows: the spend over a day, a week and 30 days, and which providers answer.
 *
 * Both lived at the top of the Tasks tab until 24/09/2026, above the missions. They moved under
 * Settings, where Claude keeps its own usage: the spend is something one checks now and then, the
 * missions something one opens the tab for. The rules they came with did not change — see each
 * field.
 */
data class UsageUiState(
    val loading: Boolean = true,
    /** Whether a scheduler is configured at all: without one there is nothing to report. */
    val schedulerConfigured: Boolean = false,
    /**
     * One report per window, read together. A window is missing while unknown — no scheduler, or
     * its gateway did not answer. Rendered as nothing rather than as zero: « 0,00 $ » on a screen
     * about money is a claim, and it would be false here.
     */
    val consumptions: Map<UsagePeriod, Consumption> = emptyMap(),
    /** The window on screen; the week, as before the other two existed. */
    val period: UsagePeriod = UsagePeriod.WEEK,
    /**
     * Null until someone asks. Obtaining it calls every model for real (~0,0015 $, two to three
     * seconds); loading it with the screen would spend money on every glance.
     */
    val providers: ProviderHealth? = null,
    val providersChecking: Boolean = false,
    val providersError: String? = null,
) {
    /** The report of the window on screen, null when that one did not answer. */
    val consumption: Consumption? get() = consumptions[period]
}

class UsageViewModel(
    private val scheduler: SchedulerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UsageUiState())
    val state: StateFlow<UsageUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val configured = runCatching { scheduler.isConfigured() }.getOrDefault(false)
            // The gateway may be down while the scheduler is fine: a failure clears only that
            // window's report, and says nothing louder than the log. The three reads go together.
            val reports = if (configured) {
                UsagePeriod.entries.map { period ->
                    async {
                        runCatching { scheduler.consumption(days = period.days) }
                            .onFailure { Logger.w(it, tag = "Usage") { "Could not read the spend over ${period.days} days" } }
                            .getOrNull()
                            ?.let { period to it }
                    }
                }.awaitAll().filterNotNull().toMap()
            } else {
                emptyMap()
            }
            _state.update { it.copy(loading = false, schedulerConfigured = configured, consumptions = reports) }
        }
    }

    fun selectPeriod(period: UsagePeriod) {
        _state.update { it.copy(period = period) }
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
                .onSuccess { health -> _state.update { it.copy(providers = health, providersChecking = false) } }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Usage") { "Could not check the providers" }
                    // The message, not a generic « failed »: the scheduler forwards the gateway's
                    // own sentence, and that sentence is the answer.
                    _state.update { it.copy(providersChecking = false, providersError = failure.message ?: "…") }
                }
        }
    }
}
