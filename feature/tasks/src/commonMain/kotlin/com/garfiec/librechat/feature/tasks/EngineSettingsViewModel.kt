package com.garfiec.librechat.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.engine.validateEngineAddresses
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The fields the form can complain about, so the messages stay in string resources. */
enum class EngineSettingsField { BASE_URL, ISSUER_URL, SCHEDULER_URL }

data class EngineSettingsUiState(
    val baseUrl: String = "",
    val issuerUrl: String = "",
    /**
     * Optional, and blank is a valid answer: the engine works without a scheduler, and the tab
     * simply has no recurring missions to show. It is validated only when it is filled in.
     */
    val schedulerUrl: String = "",
    val invalid: Set<EngineSettingsField> = emptySet(),
    val saved: Boolean = false,
)

/**
 * The form that points the app at an engine.
 *
 * It exists because everything downstream of it was written first: the OAuth flow, the token store,
 * the nine routes, the mission list. Without this screen none of it is reachable — the Tasks tab
 * can only say « not set up » and offer no way out.
 *
 * Three addresses and nothing else since D-076: no engine user, no engine password (the edge
 * presents the Basic), no client id (a constant). The portal is the one way in.
 */
class EngineSettingsViewModel(
    private val settings: EngineSettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(EngineSettingsUiState())
    val state: StateFlow<EngineSettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    /**
     * Reads what is stored back into the form.
     *
     * Called again every time the sheet opens rather than only at construction: the view model
     * outlives the sheet, so a second opening would otherwise show whatever was half-typed and
     * abandoned the first time.
     */
    fun load() {
        viewModelScope.launch {
            _state.value = EngineSettingsUiState(
                baseUrl = settings.baseUrl.first(),
                issuerUrl = settings.issuerUrl.first(),
                schedulerUrl = settings.schedulerUrl.first(),
            )
        }
    }

    fun onBaseUrl(value: String) = edit(EngineSettingsField.BASE_URL) { it.copy(baseUrl = value) }

    fun onIssuerUrl(value: String) = edit(EngineSettingsField.ISSUER_URL) { it.copy(issuerUrl = value) }

    fun onSchedulerUrl(value: String) =
        edit(EngineSettingsField.SCHEDULER_URL) { it.copy(schedulerUrl = value) }

    /** Clears the field's own complaint as it is edited; keeping it would blame a fixed field. */
    private fun edit(field: EngineSettingsField, change: (EngineSettingsUiState) -> EngineSettingsUiState) {
        _state.update { current ->
            change(current).copy(invalid = current.invalid - field, saved = false)
        }
    }

    fun save() {
        val current = _state.value
        val problems = validateEngineSettings(
            baseUrl = current.baseUrl,
            issuerUrl = current.issuerUrl,
            schedulerUrl = current.schedulerUrl,
        )
        if (problems.isNotEmpty()) {
            _state.update { it.copy(invalid = problems) }
            return
        }
        viewModelScope.launch {
            runCatching {
                settings.save(
                    baseUrl = current.baseUrl,
                    issuerUrl = current.issuerUrl,
                    schedulerUrl = current.schedulerUrl,
                )
            }
                .onSuccess { _state.update { it.copy(saved = true) } }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Tasks") { "Could not save the engine settings" }
                    _state.update { it.copy(invalid = setOf(EngineSettingsField.BASE_URL)) }
                }
        }
    }

    /** Wipes the addresses, so the tab returns to « not set up ». */
    fun forget() {
        viewModelScope.launch {
            runCatching { settings.forget() }
                .onFailure { failure -> Logger.w(failure, tag = "Tasks") { "Could not clear the engine settings" } }
            load()
        }
    }
}

/**
 * What the form refuses to save — the shared rule of `:core:data` ([validateEngineAddresses]), read
 * with this sheet's leniency: a blank scheduler is « I do not have one », not a mistake.
 */
internal fun validateEngineSettings(
    baseUrl: String,
    issuerUrl: String,
    schedulerUrl: String = "",
): Set<EngineSettingsField> =
    validateEngineAddresses(baseUrl, issuerUrl, schedulerUrl, schedulerRequired = false)
        .mapTo(HashSet()) { field -> EngineSettingsField.valueOf(field.name) }
