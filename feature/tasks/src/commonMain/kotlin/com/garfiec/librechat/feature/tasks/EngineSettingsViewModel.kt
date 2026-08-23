package com.garfiec.librechat.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The fields the form can complain about, so the messages stay in string resources. */
enum class EngineSettingsField { BASE_URL, ISSUER_URL, SCHEDULER_URL, USERNAME, PASSWORD }

data class EngineSettingsUiState(
    val loading: Boolean = true,
    val baseUrl: String = "",
    val issuerUrl: String = "",
    /**
     * Optional, and blank is a valid answer: the engine works without a scheduler, and the tab
     * simply has no recurring missions to show. It is validated only when it is filled in.
     */
    val schedulerUrl: String = "",
    val clientId: String = "",
    val username: String = "",
    /** A password is already stored. Its value is never read into this state. */
    val passwordStored: Boolean = false,
    /** What has been typed now. Blank with [passwordStored] means « keep the saved one ». */
    val password: String = "",
    val invalid: Set<EngineSettingsField> = emptySet(),
    val saved: Boolean = false,
)

/**
 * The form that points the app at an engine.
 *
 * It exists because everything downstream of it was written first: the OAuth flow, the token store,
 * the nine routes, the mission list. Without this screen none of it is reachable — the Tasks tab
 * can only say « not set up » and offer no way out.
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
                loading = false,
                baseUrl = settings.baseUrl.first(),
                issuerUrl = settings.issuerUrl.first(),
                schedulerUrl = settings.schedulerUrl.first(),
                clientId = settings.clientId.first(),
                username = settings.username.first(),
                passwordStored = settings.hasPassword(),
            )
        }
    }

    fun onBaseUrl(value: String) = edit(EngineSettingsField.BASE_URL) { it.copy(baseUrl = value) }

    fun onIssuerUrl(value: String) = edit(EngineSettingsField.ISSUER_URL) { it.copy(issuerUrl = value) }

    fun onSchedulerUrl(value: String) =
        edit(EngineSettingsField.SCHEDULER_URL) { it.copy(schedulerUrl = value) }

    fun onClientId(value: String) = edit(null) { it.copy(clientId = value) }

    fun onUsername(value: String) = edit(EngineSettingsField.USERNAME) { it.copy(username = value) }

    fun onPassword(value: String) = edit(EngineSettingsField.PASSWORD) { it.copy(password = value) }

    /** Clears the field's own complaint as it is edited; keeping it would blame a fixed field. */
    private fun edit(field: EngineSettingsField?, change: (EngineSettingsUiState) -> EngineSettingsUiState) {
        _state.update { current ->
            change(current).copy(invalid = current.invalid - setOfNotNull(field), saved = false)
        }
    }

    fun save() {
        val current = _state.value
        val problems = validateEngineSettings(
            baseUrl = current.baseUrl,
            issuerUrl = current.issuerUrl,
            username = current.username,
            typedPassword = current.password,
            passwordStored = current.passwordStored,
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
                    clientId = current.clientId.ifBlank { DEFAULT_CLIENT_ID },
                    username = current.username,
                    // Blank means « leave the stored one alone ». Sending "" would wipe a working
                    // password every time someone edits the URL and saves without retyping it.
                    password = current.password.ifBlank { null },
                    schedulerUrl = current.schedulerUrl,
                )
            }
                .onSuccess { _state.update { it.copy(saved = true, passwordStored = true, password = "") } }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Tasks") { "Could not save the engine settings" }
                    _state.update { it.copy(invalid = setOf(EngineSettingsField.BASE_URL)) }
                }
        }
    }

    /** Wipes the settings **and** the password, so the tab returns to « not set up ». */
    fun forget() {
        viewModelScope.launch {
            runCatching { settings.forget() }
                .onFailure { failure -> Logger.w(failure, tag = "Tasks") { "Could not clear the engine settings" } }
            load()
        }
    }

    private companion object {
        const val DEFAULT_CLIENT_ID = "hobbitton-chat-android"
    }
}

/**
 * What the form refuses to save, as a pure function so it can be tested without a screen.
 *
 * Stricter than [com.garfiec.librechat.core.network.engine.EngineAccess.isConfigured], deliberately:
 * that one only asks whether a request can be *built*, while this one asks whether the whole flow
 * can *work*. An engine behind Authelia with no issuer URL builds requests fine and then fails at
 * the first 302, with an error naming neither the portal nor the missing setting.
 *
 * The URL rule looks pedantic and is not: `agent.hobbitton.at` without a scheme is accepted by every
 * text field and rejected by every HTTP client, and the resulting « unknown host » reads as a
 * network outage on a phone whose network is fine.
 */
internal fun validateEngineSettings(
    baseUrl: String,
    issuerUrl: String,
    username: String,
    typedPassword: String,
    passwordStored: Boolean,
    // Last, and defaulted: the callers that predate the scheduler pass five arguments by position,
    // and slipping a sixth into the middle would compile at the definition and break at each of
    // them — which is exactly what happened when it went in after `issuerUrl`.
    schedulerUrl: String = "",
): Set<EngineSettingsField> = buildSet {
    if (!baseUrl.isHttpUrl()) add(EngineSettingsField.BASE_URL)
    if (!issuerUrl.isHttpUrl()) add(EngineSettingsField.ISSUER_URL)
    // Only when filled in: an empty scheduler URL is « I do not have one », not a mistake.
    if (schedulerUrl.isNotBlank() && !schedulerUrl.isHttpUrl()) add(EngineSettingsField.SCHEDULER_URL)
    if (username.isBlank()) add(EngineSettingsField.USERNAME)
    if (typedPassword.isBlank() && !passwordStored) add(EngineSettingsField.PASSWORD)
}

private fun String.isHttpUrl(): Boolean {
    val trimmed = trim()
    return (trimmed.startsWith("https://") || trimmed.startsWith("http://")) &&
        trimmed.substringAfter("://").isNotBlank()
}
