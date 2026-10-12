package com.garfiec.librechat.shared.engine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.scheduler.ChatBudgetSource
import com.garfiec.librechat.core.model.scheduler.ChatBudget
import com.garfiec.librechat.core.network.api.ChatBudgetRefused
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What is wrong with the amounts typed, found before anything leaves the phone. */
enum class BudgetProblem { INVALID_AMOUNT, DAY_OVER_WEEK, WEEK_OVER_MONTH }

/** The ceilings' editor: what is typed, what the gateway applies, and where a save stands. */
data class EngineBudgetUiState(
    val loading: Boolean = true,
    val day: String = "",
    val week: String = "",
    val month: String = "",
    /** What the gateway applies, to tell an edit from a no-op. Null until read. */
    val stored: ChatBudget? = null,
    /** No scheduler, or one from before `/plafonds`: nothing to edit here. */
    val unavailable: Boolean = false,
    /** The ceilings could not be read; the screen offers to try again. */
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    /** The save went through: the screen closes. */
    val saved: Boolean = false,
    val problem: BudgetProblem? = null,
    /** The scheduler's own words for a refusal, shown as they are. */
    val refusal: String? = null,
    /** The save did not reach the scheduler, or it gave no reason. */
    val saveFailed: Boolean = false,
)

/**
 * The spending ceilings of the conversations' key (12/10/2026): per day, per week, over 30 days,
 * in dollars, read from and written to the scheduler's `/plafonds`.
 *
 * Behind Enregistrer / Annuler like the instructions: a ceiling half typed must not become the
 * one that cuts a conversation. The amounts are checked here first (positive, the day under the
 * week, the week under the 30 days, D-069) so the common mistake is said at once; the scheduler
 * checks again and has the last word, its refusal shown as it words it.
 */
class EngineBudgetViewModel(
    private val source: ChatBudgetSource?,
) : ViewModel() {

    private val _state = MutableStateFlow(EngineBudgetUiState())
    val state: StateFlow<EngineBudgetUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            val budget = try {
                source?.chatBudget()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Budget: the ceilings could not be read" }
                _state.update { it.copy(loading = false, loadFailed = true) }
                return@launch
            }
            _state.update {
                if (budget == null) {
                    it.copy(loading = false, unavailable = true)
                } else {
                    it.copy(
                        loading = false,
                        stored = budget,
                        day = budget.day.asAmount(),
                        week = budget.week.asAmount(),
                        month = budget.month.asAmount(),
                    )
                }
            }
        }
    }

    fun setDay(text: String) = edit { it.copy(day = text) }

    fun setWeek(text: String) = edit { it.copy(week = text) }

    fun setMonth(text: String) = edit { it.copy(month = text) }

    private fun edit(change: (EngineBudgetUiState) -> EngineBudgetUiState) {
        _state.update { change(it).copy(problem = null, refusal = null, saveFailed = false) }
    }

    fun save() {
        val current = _state.value
        if (current.saving || current.stored == null) return
        val day = parseAmount(current.day)
        val week = parseAmount(current.week)
        val month = parseAmount(current.month)
        val problem = when {
            day == null || week == null || month == null -> BudgetProblem.INVALID_AMOUNT
            day > week -> BudgetProblem.DAY_OVER_WEEK
            week > month -> BudgetProblem.WEEK_OVER_MONTH
            else -> null
        }
        if (problem != null || day == null || week == null || month == null) {
            _state.update { it.copy(problem = problem) }
            return
        }
        if (ChatBudget(day, week, month) == current.stored) {
            _state.update { it.copy(saved = true) }
            return
        }
        _state.update { it.copy(saving = true, problem = null, refusal = null, saveFailed = false) }
        viewModelScope.launch {
            try {
                val applied = requireNotNull(source).setChatBudget(day = day, week = week, month = month)
                _state.update { it.copy(saving = false, saved = true, stored = applied) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChatBudgetRefused) {
                Logger.w(e) { "Budget: the scheduler refused the ceilings" }
                _state.update {
                    it.copy(saving = false, refusal = e.reason, saveFailed = e.reason == null)
                }
            } catch (e: Exception) {
                Logger.w(e) { "Budget: the ceilings could not be saved" }
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }
}

/** A typed amount in dollars, the decimal comma accepted; null when it is not a positive number. */
internal fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

/** An amount as the field shows it: whole dollars without decimals, cents when there are some. */
internal fun Double?.asAmount(): String {
    if (this == null) return ""
    val cents = kotlin.math.round(this * CENTS).toLong()
    val units = cents / CENTS.toLong()
    val rest = cents % CENTS.toLong()
    return if (rest == 0L) units.toString() else "$units.${rest.toString().padStart(2, '0').trimEnd('0')}"
}

private const val CENTS = 100.0
