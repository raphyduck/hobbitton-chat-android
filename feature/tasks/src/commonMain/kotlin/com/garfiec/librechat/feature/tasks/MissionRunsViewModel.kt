package com.garfiec.librechat.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.Mission
import com.garfiec.librechat.core.data.engine.engineFailureKind
import com.garfiec.librechat.core.model.engine.EngineFailureKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One scheduled mission's runs, newest first, each with its verdict. */
data class MissionRunsUiState(
    val loading: Boolean = true,
    val runs: List<Mission> = emptyList(),
    val error: EngineFailureKind? = null,
)

/**
 * Feeds the runs screen a scheduled mission's card opens (24/09/2026). The sessions used to sit in
 * one list under the Tasks tab, every mission's runs mixed; here they are one mission's, which is
 * the question one actually asks — « how did the backups go this week ».
 */
class MissionRunsViewModel(
    private val name: String,
    private val repository: EngineMissionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MissionRunsUiState())
    val state: StateFlow<MissionRunsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.missionRuns(name) }
                .onSuccess { runs -> _state.update { it.copy(loading = false, runs = runs) } }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Tasks") { "Could not read the runs of $name" }
                    _state.update { it.copy(loading = false, error = failure.engineFailureKind()) }
                }
        }
    }

    /** A run still going can be stopped from here too — the same Stop the Tasks tab offers. */
    fun abort(sessionId: String) {
        viewModelScope.launch {
            runCatching { repository.abort(sessionId) }
                .onFailure { failure -> Logger.w(failure, tag = "Tasks") { "Could not stop $sessionId" } }
            refresh()
        }
    }
}
