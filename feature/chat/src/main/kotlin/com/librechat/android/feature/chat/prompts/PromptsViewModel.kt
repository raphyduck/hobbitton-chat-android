package com.librechat.android.feature.chat.prompts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.librechat.android.core.common.result.Result
import com.librechat.android.core.data.repository.PromptRepository
import com.librechat.android.core.model.PromptGroup
import com.librechat.android.core.model.request.AddPromptToGroupRequest
import com.librechat.android.core.model.request.CreatePromptData
import com.librechat.android.core.model.request.CreatePromptGroupData
import com.librechat.android.core.model.request.CreatePromptRequest
import com.librechat.android.core.model.request.UpdatePromptGroupRequest
import com.librechat.android.core.model.request.UpdatePromptTagRequest
import com.librechat.android.feature.chat.prompts.components.PromptSortOrder
import com.librechat.android.feature.chat.prompts.components.extractVariables
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class PromptsUiState(
    val groups: List<PromptGroupDisplayData> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedGroup: PromptGroupDetailDisplayData? = null,
    val totalPages: Int = 0,
    val currentPage: Int = 1,
    // Filter and sort state
    val selectedCategory: String? = null,
    val sortOrder: PromptSortOrder = PromptSortOrder.RECENT,
    val availableCategories: List<String> = emptyList(),
    val showFilterSheet: Boolean = false,
    val filteredGroups: List<PromptGroupDisplayData> = emptyList(),
    // Share state
    val showShareDialog: Boolean = false,
    val shareGroupId: String? = null,
    // Variable dialog state
    val showVariableDialog: Boolean = false,
    val variablePromptTemplate: String = "",
    val variableNames: List<String> = emptyList(),
)

@HiltViewModel
class PromptsViewModel @Inject constructor(
    private val promptRepository: PromptRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PromptsUiState())
    val uiState: StateFlow<PromptsUiState> = _uiState.asStateFlow()

    // Keep raw domain groups for filtering/sorting by fields not in display data
    private var rawGroups: List<PromptGroup> = emptyList()

    init {
        loadGroups()
    }

    fun loadGroups() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = promptRepository.getGroups()) {
                is Result.Success -> {
                    val response = result.data
                    val groups = response.promptGroups
                    rawGroups = groups
                    val categories = groups
                        .mapNotNull { it.category }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()

                    _uiState.value = _uiState.value.copy(
                        groups = groups.map { it.toDisplayData() },
                        totalPages = if (response.hasMore) 9999 else 1,
                        currentPage = 1,
                        isLoading = false,
                        availableCategories = categories,
                    )
                    applyFilters()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message ?: "Failed to load prompts",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            when (val result = promptRepository.getGroups()) {
                is Result.Success -> {
                    val response = result.data
                    val groups = response.promptGroups
                    rawGroups = groups
                    val categories = groups
                        .mapNotNull { it.category }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()

                    _uiState.value = _uiState.value.copy(
                        groups = groups.map { it.toDisplayData() },
                        totalPages = if (response.hasMore) 9999 else 1,
                        currentPage = 1,
                        isRefreshing = false,
                        availableCategories = categories,
                    )
                    applyFilters()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = result.message ?: "Failed to refresh prompts",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun selectGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val groupResult = promptRepository.getGroup(groupId)
            val promptsResult = promptRepository.getPromptsByGroupId(groupId)

            when (groupResult) {
                is Result.Success -> {
                    val group = groupResult.data
                    val prompts = (promptsResult as? Result.Success)?.data ?: emptyList()
                    val mergedGroup = group.copy(prompts = prompts)
                    _uiState.value = _uiState.value.copy(
                        selectedGroup = mergedGroup.toDetailDisplayData(),
                        isLoading = false,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = groupResult.message ?: "Failed to load prompt details",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun clearSelectedGroup() {
        _uiState.value = _uiState.value.copy(selectedGroup = null)
    }

    fun onCategorySelected(category: String?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        applyFilters()
    }

    fun onSortOrderChanged(sortOrder: PromptSortOrder) {
        _uiState.value = _uiState.value.copy(sortOrder = sortOrder)
        applyFilters()
    }

    fun showFilterSheet() {
        _uiState.value = _uiState.value.copy(showFilterSheet = true)
    }

    fun dismissFilterSheet() {
        _uiState.value = _uiState.value.copy(showFilterSheet = false)
    }

    private fun applyFilters() {
        val state = _uiState.value
        var filtered = rawGroups

        // Apply category filter
        val selectedCat = state.selectedCategory
        if (selectedCat != null) {
            filtered = filtered.filter { it.category == selectedCat }
        }

        // Apply sort
        filtered = when (state.sortOrder) {
            PromptSortOrder.RECENT -> filtered.sortedByDescending { it.updatedAt ?: it.createdAt }
            PromptSortOrder.POPULAR -> filtered.sortedByDescending { it.numberOfGenerations }
            PromptSortOrder.ALPHABETICAL -> filtered.sortedBy { it.name.lowercase() }
        }

        _uiState.value = state.copy(filteredGroups = filtered.map { it.toDisplayData() })
    }

    fun createPrompt(promptText: String, type: String, groupName: String) {
        viewModelScope.launch {
            val request = CreatePromptRequest(
                prompt = CreatePromptData(prompt = promptText, type = type),
                group = CreatePromptGroupData(name = groupName),
            )
            try {
                promptRepository.create(request)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to create prompt",
                )
            }
        }
    }

    fun updateGroup(groupId: String, name: String?, oneliner: String?, command: String?) {
        viewModelScope.launch {
            val request = UpdatePromptGroupRequest(
                name = name,
                oneliner = oneliner,
                command = command,
            )
            try {
                promptRepository.update(groupId, request)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update prompt",
                )
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            try {
                promptRepository.delete(groupId)
                _uiState.value = _uiState.value.copy(selectedGroup = null)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete prompt",
                )
            }
        }
    }

    fun addPromptVersion(groupId: String, promptText: String) {
        viewModelScope.launch {
            val request = AddPromptToGroupRequest(prompt = promptText, type = "text")
            when (val result = promptRepository.addPromptToGroup(groupId, request)) {
                is Result.Success -> {
                    // Reload group to see updated versions
                    selectGroup(groupId)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message ?: "Failed to add prompt version",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun setProductionTag(promptId: String) {
        viewModelScope.launch {
            val request = UpdatePromptTagRequest(productionPromptId = promptId)
            when (val result = promptRepository.updatePromptProductionTag(promptId, request)) {
                is Result.Success -> {
                    // Reload the selected group to reflect the new production tag
                    val groupId = _uiState.value.selectedGroup?.id
                    if (groupId != null) {
                        selectGroup(groupId)
                    }
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message ?: "Failed to update production tag",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun deletePromptVersion(promptId: String) {
        viewModelScope.launch {
            when (val result = promptRepository.deletePrompt(promptId)) {
                is Result.Success -> {
                    val groupId = _uiState.value.selectedGroup?.id
                    if (groupId != null) {
                        selectGroup(groupId)
                    }
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message ?: "Failed to delete prompt version",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // Share

    fun showShareDialog(groupId: String) {
        _uiState.value = _uiState.value.copy(
            showShareDialog = true,
            shareGroupId = groupId,
        )
    }

    fun dismissShareDialog() {
        _uiState.value = _uiState.value.copy(
            showShareDialog = false,
            shareGroupId = null,
        )
    }

    // Variable dialog

    fun showVariableDialog(promptTemplate: String) {
        val variables = extractVariables(promptTemplate)
        if (variables.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            showVariableDialog = true,
            variablePromptTemplate = promptTemplate,
            variableNames = variables,
        )
    }

    fun dismissVariableDialog() {
        _uiState.value = _uiState.value.copy(
            showVariableDialog = false,
            variablePromptTemplate = "",
            variableNames = emptyList(),
        )
    }
}

private fun PromptGroup.toDisplayData(): PromptGroupDisplayData {
    // List endpoint: productionPrompt comes from $lookup
    // Detail endpoint: prompts array is populated
    val promptText = productionPrompt?.prompt
        ?: prompts.firstOrNull { it.id == productionId }?.prompt
        ?: prompts.firstOrNull()?.prompt
    return PromptGroupDisplayData(
        id = id ?: name,
        name = name,
        oneliner = oneliner,
        category = category,
        authorName = authorName,
        command = command,
        promptText = promptText,
    )
}

private fun PromptGroup.toDetailDisplayData(): PromptGroupDetailDisplayData {
    val promptText = productionPrompt?.prompt
        ?: prompts.firstOrNull { it.id == productionId }?.prompt
        ?: prompts.firstOrNull()?.prompt
    return PromptGroupDetailDisplayData(
        id = id ?: name,
        name = name,
        oneliner = oneliner,
        category = category,
        authorName = authorName,
        command = command,
        productionId = productionId,
        productionPromptText = promptText,
        promptCount = prompts.size,
    )
}
