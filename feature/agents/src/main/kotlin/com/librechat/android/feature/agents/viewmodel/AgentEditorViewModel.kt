package com.librechat.android.feature.agents.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.librechat.android.core.common.result.Result
import com.librechat.android.core.data.repository.AgentRepository
import com.librechat.android.core.data.repository.ConfigRepository
import com.librechat.android.core.data.repository.McpRepository
import com.librechat.android.core.model.Agent
import com.librechat.android.core.model.ActionAuth
import com.librechat.android.core.model.ActionMetadata
import com.librechat.android.core.model.AgentAction
import com.librechat.android.core.model.AgentCategory
import com.librechat.android.core.model.AgentTool
import com.librechat.android.core.model.SupportContact
import com.librechat.android.core.model.mcp.McpTool
import com.librechat.android.core.model.request.CreateActionRequest
import com.librechat.android.core.model.request.CreateAgentRequest
import com.librechat.android.core.model.request.RevertAgentRequest
import com.librechat.android.core.model.request.UpdateAgentRequest
import com.librechat.android.core.model.request.FunctionTool
import com.librechat.android.feature.agents.AgentActionDisplayData
import com.librechat.android.feature.agents.AgentHandoffDisplayData
import com.librechat.android.feature.agents.AgentToolDisplayData
import com.librechat.android.feature.agents.components.AgentAdvancedSettings
import com.librechat.android.feature.agents.util.OpenApiSpecParser
import com.librechat.android.feature.agents.components.AgentCapabilities
import com.librechat.android.feature.agents.components.AgentSharingState
import com.librechat.android.feature.agents.components.AgentVersion
import com.librechat.android.feature.agents.components.AgentVisibility
import com.librechat.android.feature.agents.components.ModelOption
import com.librechat.android.feature.agents.components.SupportContactState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import javax.inject.Inject

@Immutable
data class AgentEditorUiState(
    val isEditMode: Boolean = false,
    val agentId: String? = null,
    val name: String = "",
    val description: String = "",
    val instructions: String = "",
    val model: String = "",
    val provider: String = "",
    val category: String = "general",
    val selectedTools: List<String> = emptyList(),
    val conversationStarters: List<String> = emptyList(),
    val availableTools: List<AgentToolDisplayData> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val nameError: String? = null,
    // Advanced editor fields
    val avatarUrl: String? = null,
    val categories: List<AgentCategory> = emptyList(),
    val availableModels: List<ModelOption> = emptyList(),
    val capabilities: AgentCapabilities = AgentCapabilities(),
    val advancedSettings: AgentAdvancedSettings = AgentAdvancedSettings(),
    val versions: List<AgentVersion> = emptyList(),
    val showDeleteConfirm: Boolean = false,
    val showDuplicateConfirm: Boolean = false,
    val showVersionHistory: Boolean = false,
    val isDeleting: Boolean = false,
    val isDuplicating: Boolean = false,
    // Actions
    val actions: List<AgentActionDisplayData> = emptyList(),
    // MCP tools
    val mcpTools: List<McpTool> = emptyList(),
    val selectedMcpTools: Set<String> = emptySet(),
    // Capabilities toggles
    val codeInterpreterEnabled: Boolean = false,
    val fileSearchEnabled: Boolean = false,
    /** Whether code interpreter is available on this server (from agents endpoint capabilities). */
    val isCodeInterpreterAvailable: Boolean = true,
    // Sharing
    val sharingState: AgentSharingState = AgentSharingState(),
    // Handoff
    val handoffAgentIds: List<String> = emptyList(),
    val allAgents: List<AgentHandoffDisplayData> = emptyList(),
    // Support contact
    val supportContact: SupportContactState = SupportContactState(),
)

sealed interface AgentEditorEvent {
    data class SaveSuccess(val agentId: String) : AgentEditorEvent
    data class DuplicateSuccess(val agentId: String) : AgentEditorEvent
    data object DeleteSuccess : AgentEditorEvent
}

@HiltViewModel
class AgentEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val agentRepository: AgentRepository,
    private val configRepository: ConfigRepository,
    private val mcpRepository: McpRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val editAgentId: String? = savedStateHandle["agentId"]

    private val _uiState = MutableStateFlow(
        AgentEditorUiState(
            isEditMode = editAgentId != null,
            agentId = editAgentId,
        ),
    )
    val uiState: StateFlow<AgentEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AgentEditorEvent>()
    val events: SharedFlow<AgentEditorEvent> = _events.asSharedFlow()

    init {
        loadAvailableTools()
        loadCategories()
        loadModels()
        loadMcpTools()
        loadAllAgents()
        loadCodeInterpreterAvailability()
        if (editAgentId != null) {
            loadAgent(editAgentId)
            loadActions()
        }
    }

    /**
     * Observes the agents endpoint config capabilities to determine
     * whether code interpreter (execute_code) is available on this server.
     */
    private fun loadCodeInterpreterAvailability() {
        viewModelScope.launch {
            configRepository.endpointConfigs.collect { configs ->
                val agentsCapabilities = configs["agents"]?.capabilities ?: emptyList()
                // If capabilities list is non-empty, check for execute_code.
                // If empty (no config loaded yet), default to available.
                val available = agentsCapabilities.isEmpty() || "execute_code" in agentsCapabilities
                _uiState.value = _uiState.value.copy(isCodeInterpreterAvailable = available)
                // If code interpreter becomes unavailable, disable it
                if (!available && _uiState.value.codeInterpreterEnabled) {
                    _uiState.value = _uiState.value.copy(codeInterpreterEnabled = false)
                }
            }
        }
    }

    private fun loadAgent(agentId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            Timber.d("AgentEditor: Loading agent for editing: %s", agentId)
            // Use getAgentForEditing which calls the /expanded endpoint.
            // The standard getAgent endpoint (GET /api/agents/:id) only returns
            // basic view-only fields (id, name, description, avatar, model, provider).
            // It does NOT return instructions, tools, category, conversation_starters,
            // model_parameters, or other configuration needed for the editor.
            when (val result = agentRepository.getAgentForEditing(agentId)) {
                is Result.Success -> {
                    val agent = result.data
                    Timber.d(
                        "AgentEditor: Loaded agent fields BEFORE mapping - " +
                            "name=%s, description=%s, instructions=%s, model=%s, " +
                            "provider=%s, category=%s, tools=%s, " +
                            "conversationStarters=%s, avatarUrl=%s, " +
                            "artifacts=%s, recursionLimit=%s, " +
                            "hideSequentialOutputs=%s, endAfterTools=%s, " +
                            "isPublic=%s, isCollaborative=%s, " +
                            "agentIds=%s, supportContact=%s, " +
                            "modelParameters=%s",
                        agent.name, agent.description, agent.instructions,
                        agent.model, agent.provider, agent.category,
                        agent.tools, agent.conversationStarters,
                        agent.avatarUrl, agent.artifacts,
                        agent.recursionLimit, agent.hideSequentialOutputs,
                        agent.endAfterTools, agent.isPublic,
                        agent.isCollaborative, agent.agentIds,
                        agent.supportContact, agent.modelParameters,
                    )
                    val newState = _uiState.value
                        .applyAgentData(agent)
                        .copy(isLoading = false)
                    Timber.d(
                        "AgentEditor: UI state AFTER mapping - " +
                            "name=%s, description=%s, instructions=%s, model=%s, " +
                            "provider=%s, category=%s, selectedTools=%s, " +
                            "conversationStarters=%s, avatarUrl=%s, " +
                            "codeInterpreterEnabled=%s, fileSearchEnabled=%s, " +
                            "capabilities=%s, advancedSettings=%s, " +
                            "sharingState=%s, handoffAgentIds=%s, " +
                            "supportContact=%s",
                        newState.name, newState.description, newState.instructions,
                        newState.model, newState.provider, newState.category,
                        newState.selectedTools, newState.conversationStarters,
                        newState.avatarUrl, newState.codeInterpreterEnabled,
                        newState.fileSearchEnabled, newState.capabilities,
                        newState.advancedSettings, newState.sharingState,
                        newState.handoffAgentIds, newState.supportContact,
                    )
                    _uiState.value = newState
                }
                is Result.Error -> {
                    Timber.e(
                        "AgentEditor: Failed to load agent %s: %s",
                        agentId, result.message,
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message ?: "Failed to load agent",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun loadAvailableTools() {
        viewModelScope.launch {
            when (val result = agentRepository.getAvailableTools()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        availableTools = result.data.map { it.toDisplayData() },
                    )
                }
                is Result.Error -> { /* Tools are optional, ignore errors */ }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            when (val result = agentRepository.getAgentCategories()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        categories = result.data,
                    )
                }
                is Result.Error -> { /* Categories are optional */ }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun loadModels() {
        viewModelScope.launch {
            when (val result = configRepository.fetchModels()) {
                is Result.Success -> {
                    val modelOptions = result.data.flatMap { (endpoint, models) ->
                        models.map { modelName ->
                            ModelOption(
                                id = modelName,
                                name = modelName,
                                endpoint = endpoint,
                            )
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        availableModels = modelOptions,
                    )
                }
                is Result.Error -> { /* Models loading failed, user can retry */ }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun loadMcpTools() {
        viewModelScope.launch {
            when (val result = mcpRepository.getTools()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(mcpTools = result.data)
                }
                is Result.Error -> { /* MCP tools are optional */ }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun loadActions() {
        viewModelScope.launch {
            when (val result = agentRepository.getAgentActions()) {
                is Result.Success -> {
                    val agentId = editAgentId ?: return@launch
                    val agentActions = result.data
                        .filter { it.agentId == agentId }
                        .map { it.toDisplayData() }
                    _uiState.value = _uiState.value.copy(actions = agentActions)
                }
                is Result.Error -> { /* Actions are optional */ }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun loadAllAgents() {
        viewModelScope.launch {
            when (val result = agentRepository.getAgents()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        allAgents = result.data
                            .filter { it.id != editAgentId }
                            .map { it.toHandoffDisplayData() },
                    )
                }
                is Result.Error -> { /* Agents list is optional for handoff */ }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    // --- Basic fields ---

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name, nameError = null)
    }

    fun onDescriptionChanged(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun onInstructionsChanged(instructions: String) {
        _uiState.value = _uiState.value.copy(instructions = instructions)
    }

    fun onModelChanged(model: String) {
        _uiState.value = _uiState.value.copy(model = model)
    }

    fun onModelSelected(modelId: String, provider: String) {
        _uiState.value = _uiState.value.copy(model = modelId, provider = provider)
    }

    fun onCategoryChanged(category: String) {
        _uiState.value = _uiState.value.copy(category = category)
    }

    fun onToolToggled(toolId: String) {
        val current = _uiState.value.selectedTools
        val updated = if (toolId in current) {
            current - toolId
        } else {
            current + toolId
        }
        _uiState.value = _uiState.value.copy(selectedTools = updated)
    }

    fun onToolAdded(toolId: String) {
        val current = _uiState.value.selectedTools
        if (toolId !in current) {
            _uiState.value = _uiState.value.copy(selectedTools = current + toolId)
        }
    }

    fun onToolRemoved(toolId: String) {
        _uiState.value = _uiState.value.copy(
            selectedTools = _uiState.value.selectedTools - toolId,
        )
    }

    fun onConversationStarterAdded(starter: String) {
        if (starter.isBlank()) return
        _uiState.value = _uiState.value.copy(
            conversationStarters = _uiState.value.conversationStarters + starter.trim(),
        )
    }

    fun onConversationStarterRemoved(index: Int) {
        val updated = _uiState.value.conversationStarters.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
        }
        _uiState.value = _uiState.value.copy(conversationStarters = updated)
    }

    fun onCapabilitiesChanged(capabilities: AgentCapabilities) {
        _uiState.value = _uiState.value.copy(capabilities = capabilities)
    }

    fun onAdvancedSettingsChanged(settings: AgentAdvancedSettings) {
        _uiState.value = _uiState.value.copy(advancedSettings = settings)
    }

    // --- Support Contact ---

    fun onSupportContactChanged(supportContact: SupportContactState) {
        _uiState.value = _uiState.value.copy(supportContact = supportContact)
    }

    // --- Actions ---

    fun saveAction(
        actionId: String?,
        metadata: ActionMetadata,
        functions: List<FunctionTool>,
    ) {
        val agentId = _uiState.value.agentId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val request = CreateActionRequest(
                actionId = actionId,
                metadata = metadata,
                functions = functions,
            )
            when (val result = agentRepository.addOrUpdateAction(agentId, request)) {
                is Result.Success -> {
                    val (_, action) = result.data
                    val existing = _uiState.value.actions.toMutableList()
                    val idx = existing.indexOfFirst { it.actionId == action.actionId }
                    if (idx >= 0) {
                        existing[idx] = action.toDisplayData()
                    } else {
                        existing.add(action.toDisplayData())
                    }
                    _uiState.value = _uiState.value.copy(
                        actions = existing,
                        isSaving = false,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message ?: "Failed to save action",
                        isSaving = false,
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun deleteAction(actionId: String) {
        val agentId = _uiState.value.agentId ?: return
        viewModelScope.launch {
            when (val result = agentRepository.deleteAction(agentId, actionId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        actions = _uiState.value.actions.filter { it.actionId != actionId },
                    )
                }

                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message ?: "Failed to delete action",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    // --- MCP Tools ---

    fun onMcpToolToggled(toolName: String) {
        val current = _uiState.value.selectedMcpTools
        val updated = if (toolName in current) {
            current - toolName
        } else {
            current + toolName
        }
        _uiState.value = _uiState.value.copy(selectedMcpTools = updated)
    }

    // --- Capability toggles ---

    fun onCodeInterpreterToggled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(codeInterpreterEnabled = enabled)
    }

    fun onFileSearchToggled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(fileSearchEnabled = enabled)
    }

    // --- Sharing ---

    fun onSharingChanged(sharingState: AgentSharingState) {
        _uiState.value = _uiState.value.copy(sharingState = sharingState)
    }

    // --- Handoff ---

    fun addHandoffAgent(agentId: String) {
        val current = _uiState.value.handoffAgentIds
        if (agentId !in current) {
            _uiState.value = _uiState.value.copy(handoffAgentIds = current + agentId)
        }
    }

    fun removeHandoffAgent(agentId: String) {
        _uiState.value = _uiState.value.copy(
            handoffAgentIds = _uiState.value.handoffAgentIds - agentId,
        )
    }

    // --- Dialog state ---

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun showDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = true)
    }

    fun dismissDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = false)
    }

    fun showDuplicateConfirmation() {
        _uiState.value = _uiState.value.copy(showDuplicateConfirm = true)
    }

    fun dismissDuplicateConfirmation() {
        _uiState.value = _uiState.value.copy(showDuplicateConfirm = false)
    }

    fun showVersionHistory() {
        _uiState.value = _uiState.value.copy(showVersionHistory = true)
    }

    fun dismissVersionHistory() {
        _uiState.value = _uiState.value.copy(showVersionHistory = false)
    }

    // --- Avatar ---

    fun uploadAvatar(uri: Uri) {
        val agentId = _uiState.value.agentId ?: return
        viewModelScope.launch {
            try {
                val inputStream = appContext.contentResolver.openInputStream(uri) ?: return@launch
                val bytes = inputStream.use { it.readBytes() }
                val mimeType = appContext.contentResolver.getType(uri) ?: "image/png"

                when (val result = agentRepository.uploadAgentAvatar(agentId, bytes, mimeType)) {
                    is Result.Success -> {
                        _uiState.value = _uiState.value.copy(
                            avatarUrl = result.data.avatarUrl,
                        )
                    }
                    is Result.Error -> {
                        _uiState.value = _uiState.value.copy(
                            error = result.message ?: "Failed to upload avatar",
                        )
                    }
                    is Result.Loading -> { /* no-op */ }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to read image: ${e.message}",
                )
            }
        }
    }

    // --- Duplicate / Delete / Revert ---

    fun duplicate() {
        val agentId = _uiState.value.agentId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDuplicating = true,
                showDuplicateConfirm = false,
            )
            when (val result = agentRepository.duplicateAgent(agentId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isDuplicating = false)
                    _events.emit(AgentEditorEvent.DuplicateSuccess(result.data.id))
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

    fun delete() {
        val agentId = _uiState.value.agentId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDeleting = true,
                showDeleteConfirm = false,
            )
            when (val result = agentRepository.deleteAgent(agentId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isDeleting = false)
                    _events.emit(AgentEditorEvent.DeleteSuccess)
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

    fun revertToVersion(version: Int) {
        val agentId = _uiState.value.agentId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showVersionHistory = false, isLoading = true)
            when (val result = agentRepository.revertAgent(agentId, RevertAgentRequest(version))) {
                is Result.Success -> {
                    _uiState.value = _uiState.value
                        .applyAgentData(result.data)
                        .copy(isLoading = false)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message ?: "Failed to revert agent",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    // --- Save ---

    fun save() {
        val state = _uiState.value

        // Validate
        if (state.name.isBlank()) {
            _uiState.value = state.copy(nameError = "Name is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            val isPublic = state.sharingState.visibility == AgentVisibility.PUBLIC
            val isCollaborative = state.sharingState.isCollaborative

            val supportContact = if (state.supportContact.name.isNotBlank() ||
                state.supportContact.email.isNotBlank()
            ) {
                SupportContact(
                    name = state.supportContact.name.ifBlank { null },
                    email = state.supportContact.email.ifBlank { null },
                )
            } else {
                null
            }

            // Build the full tools list: user-selected tools + capability tools + MCP server markers
            val allTools = buildToolsList(state)

            // Build model_parameters from advanced settings
            val modelParameters = buildModelParameters(state.advancedSettings)

            // Artifacts string: the backend stores this as a string, not boolean.
            // The AgentCapabilities.artifacts boolean maps to an empty string or non-empty.
            val artifacts = if (state.capabilities.artifacts) "artifacts" else null

            val result = if (state.isEditMode && state.agentId != null) {
                agentRepository.updateAgent(
                    id = state.agentId,
                    request = UpdateAgentRequest(
                        name = state.name,
                        description = state.description.ifBlank { null },
                        instructions = state.instructions.ifBlank { null },
                        model = state.model.ifBlank { null },
                        provider = state.provider.ifBlank { null },
                        modelParameters = modelParameters,
                        artifacts = artifacts,
                        recursionLimit = state.capabilities.recursionLimit,
                        hideSequentialOutputs = state.capabilities.hideSequentialOutputs,
                        endAfterTools = state.capabilities.endAfterTools,
                        category = state.category.ifBlank { null },
                        tools = allTools.ifEmpty { null },
                        conversationStarters = state.conversationStarters.ifEmpty { null },
                        isPublic = isPublic,
                        isCollaborative = isCollaborative,
                        supportContact = supportContact,
                    ),
                )
            } else {
                agentRepository.createAgent(
                    request = CreateAgentRequest(
                        name = state.name,
                        description = state.description.ifBlank { null },
                        instructions = state.instructions.ifBlank { null },
                        model = state.model.ifBlank { null },
                        provider = state.provider.ifBlank { null },
                        modelParameters = modelParameters,
                        artifacts = artifacts,
                        recursionLimit = state.capabilities.recursionLimit,
                        hideSequentialOutputs = state.capabilities.hideSequentialOutputs,
                        endAfterTools = state.capabilities.endAfterTools,
                        category = state.category.ifBlank { null },
                        tools = allTools.ifEmpty { null },
                        conversationStarters = state.conversationStarters.ifEmpty { null },
                        isPublic = isPublic,
                        isCollaborative = isCollaborative,
                        supportContact = supportContact,
                    ),
                )
            }

            when (result) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _events.emit(AgentEditorEvent.SaveSuccess(result.data.id))
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = result.message ?: "Failed to save agent",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    companion object {

        // Tool identifiers that represent capabilities (not user-selectable tools).
        // These are stored in the agent's tools list but displayed as capability toggles in the UI.
        private val CAPABILITY_TOOLS = setOf(
            "execute_code",
            "file_search",
            "web_search",
            "end_after_tools",
            "hide_sequential_outputs",
        )

        // MCP server marker prefix: tools starting with "sys__server__sys" or containing "_mcp_"
        // are MCP-related entries in the tools list.
        private const val MCP_SERVER_MARKER = "sys__server__sys"
        private const val MCP_TOOL_SEPARATOR = "_mcp_"

        /**
         * Partitions the agent's raw tools list into:
         * - regular tools (for the tool selector UI)
         * - capability booleans (code interpreter, file search, etc.)
         * - selected MCP tool names (tools containing "_mcp_")
         *
         * This mirrors how the web frontend splits the tools array when loading
         * an agent for editing (see AgentSelect.tsx resetAgentForm).
         */
        private fun partitionTools(
            rawTools: List<String>?,
        ): Triple<List<String>, Set<String>, Set<String>> {
            if (rawTools == null) return Triple(emptyList(), emptySet(), emptySet())

            val regularTools = mutableListOf<String>()
            val capabilityTools = mutableSetOf<String>()
            val mcpToolNames = mutableSetOf<String>()

            for (tool in rawTools) {
                when {
                    tool in CAPABILITY_TOOLS -> capabilityTools.add(tool)
                    tool.contains(MCP_TOOL_SEPARATOR) -> {
                        // MCP tools are stored as "toolName_mcp_serverName" or
                        // "sys__server__sys_mcp_serverName" in the agent's tools list.
                        // Extract the tool name part (before _mcp_) for matching against
                        // the McpTool.name from the MCP tools API.
                        val toolName = tool.substringBefore(MCP_TOOL_SEPARATOR)
                        // The sys__server__sys marker means the entire MCP server was
                        // toggled on -- we still track the server name for display.
                        if (toolName == MCP_SERVER_MARKER) {
                            // Server-level toggle: store the server name
                            val serverName = tool.substringAfter(MCP_TOOL_SEPARATOR)
                            mcpToolNames.add(serverName)
                        } else {
                            mcpToolNames.add(toolName)
                        }
                    }
                    else -> regularTools.add(tool)
                }
            }

            return Triple(regularTools, capabilityTools, mcpToolNames)
        }

        /**
         * Applies agent data to the UI state using the copy() function.
         * Returns a new AgentEditorUiState with all agent fields populated.
         * This is the single source of truth for mapping agent API response data
         * to the editor UI, used by both loadAgent() and revertToVersion().
         */
        private fun AgentEditorUiState.applyAgentData(agent: Agent): AgentEditorUiState {
            val (regularTools, capabilityTools, mcpToolNames) = partitionTools(agent.tools)

            return copy(
                name = agent.name ?: "",
                description = agent.description ?: "",
                instructions = agent.instructions ?: "",
                model = agent.model ?: "",
                provider = agent.provider ?: "",
                category = agent.category ?: "general",
                selectedTools = regularTools,
                conversationStarters = agent.conversationStarters,
                avatarUrl = agent.avatarUrl,
                codeInterpreterEnabled = "execute_code" in capabilityTools,
                fileSearchEnabled = "file_search" in capabilityTools,
                selectedMcpTools = mcpToolNames,
                capabilities = AgentCapabilities(
                    artifacts = !agent.artifacts.isNullOrBlank(),
                    endAfterTools = agent.endAfterTools ?: false,
                    hideSequentialOutputs = agent.hideSequentialOutputs ?: false,
                    recursionLimit = agent.recursionLimit ?: 25,
                ),
                advancedSettings = parseModelParameters(agent.modelParameters),
                sharingState = AgentSharingState(
                    visibility = when {
                        agent.isPublic == true -> AgentVisibility.PUBLIC
                        agent.isCollaborative == true -> AgentVisibility.TEAM
                        else -> AgentVisibility.PRIVATE
                    },
                    isCollaborative = agent.isCollaborative ?: false,
                ),
                supportContact = agent.parseSupportContact(),
                handoffAgentIds = agent.agentIds ?: emptyList(),
            )
        }

        /**
         * Parse model_parameters JsonElement into AgentAdvancedSettings.
         * The backend stores these as: { "temperature": 0.7, "top_p": 0.9, "max_tokens": 4096 }
         */
        private fun parseModelParameters(params: kotlinx.serialization.json.JsonElement?): AgentAdvancedSettings {
            if (params == null) return AgentAdvancedSettings()
            return try {
                val obj = params.jsonObject
                AgentAdvancedSettings(
                    temperature = obj["temperature"]?.jsonPrimitive?.floatOrNull,
                    topP = (obj["top_p"] ?: obj["topP"])?.jsonPrimitive?.floatOrNull,
                    maxTokens = (obj["max_tokens"] ?: obj["maxTokens"])?.jsonPrimitive?.intOrNull,
                )
            } catch (_: Exception) {
                AgentAdvancedSettings()
            }
        }

        /**
         * Build model_parameters JsonObject from the advanced settings.
         * Returns null if no parameters are set (to avoid sending empty objects).
         */
        private fun buildModelParameters(settings: AgentAdvancedSettings): JsonObject? {
            val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
            settings.temperature?.let { map["temperature"] = JsonPrimitive(it) }
            settings.topP?.let { map["top_p"] = JsonPrimitive(it) }
            settings.maxTokens?.let { map["max_tokens"] = JsonPrimitive(it) }
            return if (map.isEmpty()) null else JsonObject(map)
        }

        /**
         * Build the full tools list for saving, combining:
         * - User-selected tools (from tool selector)
         * - Capability tools (execute_code, file_search) based on toggle state
         * - MCP server markers for selected MCP tools
         */
        private fun buildToolsList(state: AgentEditorUiState): List<String> {
            val tools = state.selectedTools.toMutableList()

            // Add capability tools based on toggle state
            if (state.codeInterpreterEnabled) tools.add("execute_code")
            if (state.fileSearchEnabled) tools.add("file_search")

            // Add MCP server markers for each selected MCP tool
            for (mcpToolName in state.selectedMcpTools) {
                // Check if this is a server name or a tool name by looking at available MCP tools
                val matchingTool = state.mcpTools.find { it.name == mcpToolName }
                if (matchingTool != null) {
                    val serverName = matchingTool.serverName
                    if (serverName != null) {
                        // Store as "toolName_mcp_serverName" format
                        tools.add("${mcpToolName}${MCP_TOOL_SEPARATOR}${serverName}")
                    } else {
                        tools.add(mcpToolName)
                    }
                } else {
                    // May be a server name marker
                    tools.add("${MCP_SERVER_MARKER}${MCP_TOOL_SEPARATOR}${mcpToolName}")
                }
            }

            return tools
        }

        private fun Agent.toHandoffDisplayData() = AgentHandoffDisplayData(
            id = id,
            name = name ?: id,
        )

        private fun AgentAction.toDisplayData(): AgentActionDisplayData {
            val rawSpec = metadata?.rawSpec
            val functionCount = if (!rawSpec.isNullOrBlank()) {
                try {
                    OpenApiSpecParser.extractFunctionInfo(rawSpec).size
                } catch (_: Exception) {
                    0
                }
            } else {
                0
            }
            return AgentActionDisplayData(
                actionId = actionId,
                domain = metadata?.domain,
                type = type,
                authType = metadata?.auth?.type,
                rawSpec = metadata?.rawSpec,
                functionCount = functionCount,
            )
        }

        private fun AgentTool.toDisplayData() = AgentToolDisplayData(
            toolId = pluginKey ?: toolId,
            name = name,
            description = description,
            icon = icon,
            isAvailable = isAvailable,
        )

        /**
         * Parse the support_contact JsonElement from the Agent model into
         * a SupportContactState for the UI.
         */
        private fun Agent.parseSupportContact(): SupportContactState {
            val json = supportContact ?: return SupportContactState()
            return try {
                val obj = json as? kotlinx.serialization.json.JsonObject
                    ?: return SupportContactState()
                val name = obj["name"]
                    ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    ?: ""
                val email = obj["email"]
                    ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    ?: ""
                SupportContactState(name = name, email = email)
            } catch (_: Exception) {
                SupportContactState()
            }
        }
    }
}
