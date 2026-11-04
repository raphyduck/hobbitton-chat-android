package com.garfiec.librechat.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.scheduler.SchedulerRepository
import com.garfiec.librechat.core.model.scheduler.MissionEdits
import com.garfiec.librechat.core.model.scheduler.ScheduledMission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The mission the screen is for, handed over by the navigation layer through Koin's parameters. */
data class MissionDetailArgs(val name: String)

/** What the screen says after a save or an action; mapped to words by the screen, not here. */
sealed interface MissionNotice {
    data object Saved : MissionNotice
    data class Failed(val detail: String) : MissionNotice
}

data class MissionDetailUiState(
    val name: String,
    val mission: ScheduledMission? = null,
    val loading: Boolean = true,
    /** Why the mission could not be read, when it could not. */
    val error: String? = null,
    /** The gateway's model ids, to pick one for the mission; empty when the prices did not come. */
    val models: List<String> = emptyList(),
    val saving: Boolean = false,
    val notice: MissionNotice? = null,
    /** The mission is gone: the screen leaves. */
    val deleted: Boolean = false,
)

/**
 * One scheduled mission, read in full and changed field by field (04/11/2026, Raphaël: see
 * what runs a mission, its model, its tier, its prompt, and edit them).
 *
 * The scheduler's `modifier` merges: only what the person touched travels, and the prompt is
 * never resent unchanged. A failed save is a notice, not an error screen: the mission is still
 * there, re-read right after.
 */
class MissionDetailViewModel(
    private val name: String,
    private val scheduler: SchedulerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MissionDetailUiState(name = name))
    val state: StateFlow<MissionDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { scheduler.mission(name) }
                .onSuccess { mission -> _state.update { it.copy(loading = false, mission = mission) } }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Mission") { "Could not read $name" }
                    _state.update { it.copy(loading = false, error = failure.message) }
                }
            if (_state.value.models.isEmpty()) {
                runCatching { scheduler.fetch().models.map { it.model }.sorted() }
                    .onSuccess { models -> _state.update { it.copy(models = models) } }
            }
        }
    }

    fun save(edits: MissionEdits) {
        if (edits.isEmpty) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val notice = runCatching { scheduler.updateMission(name, edits) }
                .fold(
                    onSuccess = { answer ->
                        if (answer.startsWith("ERREUR")) MissionNotice.Failed(answer) else MissionNotice.Saved
                    },
                    onFailure = { failure ->
                        Logger.w(failure, tag = "Mission") { "Could not save $name" }
                        MissionNotice.Failed(failure.message.orEmpty())
                    },
                )
            _state.update { it.copy(saving = false, notice = notice) }
            load()
        }
    }

    fun run() = actThenReload("start") { scheduler.run(name) }

    fun setEnabled(enabled: Boolean) = actThenReload("toggle") { scheduler.setEnabled(name, enabled) }

    fun delete() {
        viewModelScope.launch {
            runCatching { scheduler.deleteMission(name) }
                .onSuccess { _state.update { it.copy(deleted = true) } }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Mission") { "Could not delete $name" }
                    _state.update { it.copy(notice = MissionNotice.Failed(failure.message.orEmpty())) }
                }
        }
    }

    fun noticeShown() = _state.update { it.copy(notice = null) }

    private fun actThenReload(what: String, action: suspend () -> Any?) {
        viewModelScope.launch {
            runCatching { action() }
                .onFailure { failure ->
                    Logger.w(failure, tag = "Mission") { "Could not $what $name" }
                    _state.update { it.copy(notice = MissionNotice.Failed(failure.message.orEmpty())) }
                }
            load()
        }
    }
}
