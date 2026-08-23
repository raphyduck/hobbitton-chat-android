package com.garfiec.librechat.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.engine.Mission
import com.garfiec.librechat.core.data.engine.engineFailureKind
import com.garfiec.librechat.core.model.engine.EngineFailureKind
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
    val profiles: List<String> = emptyList(),
    /** Why the last call failed, or null. The screen turns it into a sentence and an offer. */
    val error: EngineFailureKind? = null,
)

class TasksViewModel(
    private val repository: EngineMissionRepository,
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

    fun abort(sessionId: String) {
        viewModelScope.launch {
            runCatching { repository.abort(sessionId) }
                .onFailure { failure -> Logger.w(failure, tag = "Tasks") { "Could not stop $sessionId" } }
            refresh()
        }
    }
}
