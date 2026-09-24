package com.garfiec.librechat.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.scheduler.SchedulerRepository
import com.garfiec.librechat.core.model.scheduler.Consumption
import com.garfiec.librechat.core.model.scheduler.ProviderHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the Usage screen shows: the week's spend and which providers answer.
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
     * Null while unknown — no scheduler, or its gateway did not answer. Rendered as nothing rather
     * than as zero: « 0,00 $ » on a screen about money is a claim, and it would be false here.
     */
    val consumption: Consumption? = null,
    /**
     * Null until someone asks. Obtaining it calls every model for real (~0,0015 $, two to three
     * seconds); loading it with the screen would spend money on every glance.
     */
    val providers: ProviderHealth? = null,
    val providersChecking: Boolean = false,
    val providersError: String? = null,
)

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
            // The gateway may be down while the scheduler is fine: a failure clears only the
            // report, and says nothing louder than the log.
            val report = if (configured) {
                runCatching { scheduler.consumption(days = CONSUMPTION_DAYS) }
                    .onFailure { Logger.w(it, tag = "Usage") { "Could not read the week's spend" } }
                    .getOrNull()
            } else {
                null
            }
            _state.update { it.copy(loading = false, schedulerConfigured = configured, consumption = report) }
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
                .onSuccess { health -> _state.update { it.copy(providers = health, providersChecking = false) } }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Usage") { "Could not check the providers" }
                    // The message, not a generic « failed »: the scheduler forwards the gateway's
                    // own sentence, and that sentence is the answer.
                    _state.update { it.copy(providersChecking = false, providersError = failure.message ?: "…") }
                }
        }
    }

    private companion object {
        /** A week: long enough to see a trend, short enough that today still stands out. */
        const val CONSUMPTION_DAYS = 7
    }
}
