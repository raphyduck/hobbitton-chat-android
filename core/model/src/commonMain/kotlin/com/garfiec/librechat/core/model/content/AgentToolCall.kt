package com.garfiec.librechat.core.model.content

import com.garfiec.librechat.core.model.ToolCallType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AgentToolCall(
    val type: ToolCallType? = null,
    val name: String? = null,
    val args: JsonElement? = null,
    val id: String? = null,
    val output: String? = null,
    val auth: String? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val function: FunctionCall? = null,
)
