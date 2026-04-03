package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ToolAuthStatus(
    @SerialName("tool_id") val toolId: String? = null,
    val authenticated: Boolean = false,
    @SerialName("auth_type") val authType: String? = null,
    @SerialName("auth_url") val authUrl: String? = null,
    val message: String? = null,
)
