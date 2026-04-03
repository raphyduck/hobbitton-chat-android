package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.result.safeApiCall
import com.librechat.android.core.model.mcp.McpApiKeyConfig
import com.librechat.android.core.model.mcp.McpOAuthConfig
import com.librechat.android.core.model.mcp.McpReinitializeResponse
import com.librechat.android.core.model.mcp.McpServer
import com.librechat.android.core.model.mcp.McpServerStatus
import com.librechat.android.core.model.mcp.McpServerType
import com.librechat.android.core.model.mcp.McpTool
import com.librechat.android.core.network.api.McpApi

class McpRepositoryImpl(
    private val mcpApi: McpApi,
) : McpRepository {

    override suspend fun listServers(): Result<List<McpServer>> =
        safeApiCall { mcpApi.listServers() }

    override suspend fun createServer(
        name: String,
        description: String?,
        url: String,
        type: McpServerType,
        apiKey: McpApiKeyConfig?,
        oauth: McpOAuthConfig?,
    ): Result<McpServer> =
        safeApiCall { mcpApi.createServer(name, description, url, type, apiKey, oauth) }

    override suspend fun deleteServer(serverName: String): Result<Unit> =
        safeApiCall { mcpApi.deleteServer(serverName) }

    override suspend fun reinitialize(serverName: String): Result<McpReinitializeResponse> =
        safeApiCall { mcpApi.reinitialize(serverName) }

    override suspend fun getTools(): Result<List<McpTool>> =
        safeApiCall { mcpApi.getTools() }

    override suspend fun getConnectionStatus(): Result<Map<String, McpServerStatus>> =
        safeApiCall { mcpApi.getConnectionStatus() }
}
