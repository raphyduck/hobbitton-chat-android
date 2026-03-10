package com.librechat.android.feature.settings.viewmodel.delegate

import android.util.Log
import com.librechat.android.core.common.result.Result
import com.librechat.android.core.data.repository.McpRepository
import com.librechat.android.core.model.mcp.McpApiKeyConfig
import com.librechat.android.core.model.mcp.McpOAuthConfig
import com.librechat.android.core.model.mcp.McpServer
import com.librechat.android.core.model.mcp.McpServerType
import com.librechat.android.feature.settings.viewmodel.SettingsStateHandle
import kotlinx.coroutines.launch

/**
 * Handles MCP server management, connection status, and reinitialization.
 */
class McpServerDelegate(
    private val stateHandle: SettingsStateHandle,
    private val mcpRepository: McpRepository,
) {

    fun loadMcpServers() {
        stateHandle.scope.launch {
            when (val result = mcpRepository.listServers()) {
                is Result.Success -> {
                    stateHandle.update { copy(mcpServers = result.data, mcpError = null) }
                }
                is Result.Error -> {
                    Log.d("SettingsViewModel", "Failed to load MCP servers: ${result.message}", result.exception)
                    stateHandle.update { copy(mcpError = result.message ?: "MCP not available on this server") }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
        stateHandle.scope.launch {
            when (val result = mcpRepository.getConnectionStatus()) {
                is Result.Success -> {
                    stateHandle.update { copy(mcpConnectionStatus = result.data) }
                }
                is Result.Error -> {
                    Log.d("SettingsViewModel", "Failed to load MCP connection status: ${result.message}", result.exception)
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun showAddMcpServerDialog() {
        stateHandle.update { copy(showMcpServerDialog = true, editingMcpServer = null) }
    }

    fun showEditMcpServerDialog(server: McpServer) {
        stateHandle.update { copy(showMcpServerDialog = true, editingMcpServer = server) }
    }

    fun dismissMcpServerDialog() {
        stateHandle.update { copy(showMcpServerDialog = false, editingMcpServer = null) }
    }

    fun saveMcpServer(
        name: String,
        description: String? = null,
        url: String,
        type: McpServerType,
        apiKey: McpApiKeyConfig? = null,
        oauth: McpOAuthConfig? = null,
    ) {
        stateHandle.scope.launch {
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
                    dismissMcpServerDialog()
                    loadMcpServers()
                }
                is Result.Error -> {
                    stateHandle.update { copy(error = result.message ?: "Failed to save MCP server") }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun deleteMcpServer(serverName: String) {
        stateHandle.scope.launch {
            when (val result = mcpRepository.deleteServer(serverName)) {
                is Result.Success -> loadMcpServers()
                is Result.Error -> {
                    stateHandle.update { copy(error = result.message ?: "Failed to delete MCP server") }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun reinitializeMcpServer(serverName: String) {
        stateHandle.scope.launch {
            stateHandle.update { copy(mcpReinitializingServers = mcpReinitializingServers + serverName) }
            when (val result = mcpRepository.reinitialize(serverName)) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(
                            mcpReinitializingServers = mcpReinitializingServers - serverName,
                            mcpReinitializeMessage = "Server reinitialized successfully",
                        )
                    }
                    loadMcpServers()
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            mcpReinitializingServers = mcpReinitializingServers - serverName,
                            mcpReinitializeMessage = result.message ?: "Failed to reinitialize server",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun dismissMcpReinitializeMessage() {
        stateHandle.update { copy(mcpReinitializeMessage = null) }
    }
}
