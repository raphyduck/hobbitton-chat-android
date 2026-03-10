package com.librechat.android.core.model.mcp

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an MCP server as shown in the UI.
 * The backend returns a map of server name -> config from GET /api/mcp/servers.
 * This model is constructed from that map in the API layer.
 */
@Immutable
data class McpServer(
    val name: String,
    val url: String = "",
    val type: McpServerType = McpServerType.SSE,
    val title: String? = null,
    val description: String? = null,
    val tools: List<McpTool> = emptyList(),
    val isConnected: Boolean = false,
    val error: String? = null,
    val apiKey: McpApiKeyConfig? = null,
    val oauth: McpOAuthConfig? = null,
)

@Serializable
enum class McpServerType {
    @SerialName("sse") SSE,
    @SerialName("streamable-http") STREAMABLE_HTTP,
    @SerialName("http") HTTP,
    @SerialName("stdio") STDIO,
    @SerialName("websocket") WEBSOCKET,
}

/** Auth mode for the MCP server add/edit dialog. */
enum class McpAuthMode { NONE, API_KEY, OAUTH }

/** Maps to backend apiKey object: { source, authorization_type, key?, custom_header? }. */
@Immutable
@Serializable
data class McpApiKeyConfig(
    val source: McpApiKeySource = McpApiKeySource.USER,
    @SerialName("authorization_type") val authorizationType: McpAuthorizationType = McpAuthorizationType.BEARER,
    val key: String? = null,
    @SerialName("custom_header") val customHeader: String? = null,
)

@Serializable
enum class McpApiKeySource {
    @SerialName("admin") ADMIN,
    @SerialName("user") USER,
}

@Serializable
enum class McpAuthorizationType {
    @SerialName("bearer") BEARER,
    @SerialName("basic") BASIC,
    @SerialName("custom") CUSTOM,
}

/** Maps to backend oauth object. Only the most common fields are exposed in the UI. */
@Immutable
@Serializable
data class McpOAuthConfig(
    @SerialName("authorization_url") val authorizationUrl: String? = null,
    @SerialName("token_url") val tokenUrl: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("client_secret") val clientSecret: String? = null,
    val scope: String? = null,
)
