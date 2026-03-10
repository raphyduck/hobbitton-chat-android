package com.librechat.android.feature.agents.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.librechat.android.core.common.result.Result
import com.librechat.android.core.data.datastore.ServerDataStore
import com.librechat.android.core.data.repository.AgentRepository
import com.librechat.android.core.model.Agent
import com.librechat.android.feature.agents.AgentDetailDisplayData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class AgentDetailUiState(
    val agent: AgentDetailDisplayData? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showDeleteDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val isDuplicating: Boolean = false,
)

sealed interface AgentDetailEvent {
    data object Deleted : AgentDetailEvent
    data class Duplicated(val agentId: String) : AgentDetailEvent
}

@HiltViewModel
class AgentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val agentRepository: AgentRepository,
    private val serverDataStore: ServerDataStore,
) : ViewModel() {

    private val agentId: String = checkNotNull(savedStateHandle["agentId"])

    private val _uiState = MutableStateFlow(AgentDetailUiState())
    val uiState: StateFlow<AgentDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AgentDetailEvent>()
    val events: SharedFlow<AgentDetailEvent> = _events.asSharedFlow()

    init {
        loadAgent()
    }

    fun loadAgent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            // Try the expanded endpoint first (returns full agent data including
            // description, category, tools, conversation_starters). Falls back to
            // the standard endpoint if the user lacks edit permission (403).
            val result = when (val expanded = agentRepository.getAgentForEditing(agentId)) {
                is Result.Success -> expanded
                else -> agentRepository.getAgent(agentId)
            }
            when (result) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        agent = result.data.toDetailDisplayData(),
                        isLoading = false,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message ?: "Failed to load agent",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun showDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = true)
    }

    fun dismissDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = false)
    }

    fun deleteAgent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDeleting = true,
                showDeleteDialog = false,
                error = null,
            )
            when (val result = agentRepository.deleteAgent(agentId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isDeleting = false)
                    _events.emit(AgentDetailEvent.Deleted)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        error = result.message ?: "Failed to delete agent",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun duplicateAgent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDuplicating = true, error = null)
            when (val result = agentRepository.duplicateAgent(agentId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isDuplicating = false)
                    _events.emit(AgentDetailEvent.Duplicated(result.data.id))
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isDuplicating = false,
                        error = result.message ?: "Failed to duplicate agent",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun Agent.toDetailDisplayData(): AgentDetailDisplayData {
        val resolvedUrl = avatarUrl?.let { url ->
            if (url.startsWith("http")) url
            else "${serverDataStore.getBaseUrl()}$url"
        }
        return AgentDetailDisplayData(
            id = id,
            name = name ?: "Unnamed Agent",
            description = description,
            avatarUrl = resolvedUrl,
            author = author,
            authorName = authorName,
            model = model,
            category = category,
            tools = tools,
            conversationStarters = conversationStarters,
        )
    }
}
