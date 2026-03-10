package com.librechat.android.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.librechat.android.core.common.result.Result
import com.librechat.android.core.data.repository.McpRepository
import com.librechat.android.core.model.mcp.McpApiKeyConfig
import com.librechat.android.core.model.mcp.McpOAuthConfig
import com.librechat.android.core.model.mcp.McpServer
import com.librechat.android.core.model.mcp.McpServerStatus
import com.librechat.android.core.model.mcp.McpServerType
import com.librechat.android.core.model.mcp.McpTool
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject

@Immutable
data class McpUiState(
    val servers: List<McpServer> = emptyList(),
    val connectionStatus: Map<String, McpServerStatus> = emptyMap(),
    val tools: List<McpTool> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val reinitializingServers: Set<String> = emptySet(),
    val error: String? = null,
    val showServerDialog: Boolean = false,
    val editingServer: McpServer? = null,
    val showToolsSheet: Boolean = false,
    val toolsSheetServerName: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class McpViewModel @Inject constructor(
    private val mcpRepository: McpRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(McpUiState())
    val uiState: StateFlow<McpUiState> = _uiState.asStateFlow()

    init {
        loadServers()
        loadConnectionStatus()
        loadTools()
    }

    fun loadServers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = _uiState.value.servers.isEmpty())
            when (val result = mcpRepository.listServers()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        servers = result.data,
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = result.message ?: "Failed to load servers",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun loadConnectionStatus() {
        viewModelScope.launch {
            when (val result = mcpRepository.getConnectionStatus()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(connectionStatus = result.data)
                }
                is Result.Error -> {
                    Log.d("McpViewModel", "Failed to load connection status: ${result.message}", result.exception)
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun loadTools() {
        viewModelScope.launch {
            when (val result = mcpRepository.getTools()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(tools = result.data)
                }
                is Result.Error -> {
                    Log.d("McpViewModel", "Failed to load tools: ${result.message}", result.exception)
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadServers()
        loadConnectionStatus()
        loadTools()
    }

    fun showAddServerDialog() {
        _uiState.value = _uiState.value.copy(showServerDialog = true, editingServer = null)
    }

    fun showEditServerDialog(server: McpServer) {
        _uiState.value = _uiState.value.copy(showServerDialog = true, editingServer = server)
    }

    fun dismissServerDialog() {
        _uiState.value = _uiState.value.copy(showServerDialog = false, editingServer = null)
    }

    fun saveServer(
        name: String,
        description: String? = null,
        url: String,
        type: McpServerType,
        apiKey: McpApiKeyConfig? = null,
        oauth: McpOAuthConfig? = null,
    ) {
        viewModelScope.launch {
            val result = mcpRepository.createServer(
                name = name,
                description = description,
                url = url,
                type = type,
                apiKey = apiKey,
                oauth = oauth,
            )
            when (result) {
                is Result.Success -> {
                    dismissServerDialog()
                    loadServers()
                    loadConnectionStatus()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message ?: "Failed to save server",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun deleteServer(serverName: String) {
        viewModelScope.launch {
            when (val result = mcpRepository.deleteServer(serverName)) {
                is Result.Success -> {
                    loadServers()
                    loadConnectionStatus()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message ?: "Failed to delete server",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun reinitializeServer(serverName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                reinitializingServers = _uiState.value.reinitializingServers + serverName,
            )
            when (val result = mcpRepository.reinitialize(serverName)) {
                is Result.Success -> {
                    val response = result.data
                    val message = response.message ?: if (response.success) {
                        "Server initialized successfully"
                    } else {
                        "Failed to initialize server"
                    }
                    _uiState.value = _uiState.value.copy(
                        reinitializingServers = _uiState.value.reinitializingServers - serverName,
                        successMessage = message,
                    )
                    loadServers()
                    loadConnectionStatus()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        reinitializingServers = _uiState.value.reinitializingServers - serverName,
                        error = result.message ?: "Failed to reinitialize server",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun showToolsSheet(serverName: String? = null) {
        _uiState.value = _uiState.value.copy(
            showToolsSheet = true,
            toolsSheetServerName = serverName,
        )
    }

    fun dismissToolsSheet() {
        _uiState.value = _uiState.value.copy(
            showToolsSheet = false,
            toolsSheetServerName = null,
        )
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
