package com.garfiec.librechat.core.model.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateMcpServerRequest(
    val name: String,
    val url: String,
    val type: McpServerType = McpServerType.SSE,
    @SerialName("api_key") val apiKey: String? = null,
)

@Serializable
data class UpdateMcpServerRequest(
    val url: String? = null,
    val type: McpServerType? = null,
    @SerialName("api_key") val apiKey: String? = null,
)

@Serializable
data class McpReinitializeResponse(
    val success: Boolean = false,
    val message: String? = null,
    val serverName: String? = null,
    val oauthRequired: Boolean? = null,
    val oauthUrl: String? = null,
)
