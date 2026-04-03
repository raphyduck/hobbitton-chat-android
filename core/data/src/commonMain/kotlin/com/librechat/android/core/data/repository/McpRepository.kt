package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.mcp.McpApiKeyConfig
import com.librechat.android.core.model.mcp.McpOAuthConfig
import com.librechat.android.core.model.mcp.McpReinitializeResponse
import com.librechat.android.core.model.mcp.McpServer
import com.librechat.android.core.model.mcp.McpServerStatus
import com.librechat.android.core.model.mcp.McpServerType
import com.librechat.android.core.model.mcp.McpTool

/** Repository for MCP server management. Uses serverName as unique identifier for operations. */
interface McpRepository {
    suspend fun listServers(): Result<List<McpServer>>
    suspend fun createServer(
        name: String,
        description: String? = null,
        url: String,
        type: McpServerType,
        apiKey: McpApiKeyConfig? = null,
        oauth: McpOAuthConfig? = null,
    ): Result<McpServer>
    suspend fun deleteServer(serverName: String): Result<Unit>
    suspend fun reinitialize(serverName: String): Result<McpReinitializeResponse>
    suspend fun getTools(): Result<List<McpTool>>
    suspend fun getConnectionStatus(): Result<Map<String, McpServerStatus>>
}
