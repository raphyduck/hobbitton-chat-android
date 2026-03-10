package com.librechat.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ToolCallRecord(
    val id: String? = null,
    @SerialName("tool_id") val toolId: String? = null,
    @SerialName("agent_id") val agentId: String? = null,
    @SerialName("conversation_id") val conversationId: String? = null,
    val name: String? = null,
    val input: JsonElement? = null,
    val output: JsonElement? = null,
    val status: String? = null,
    val error: String? = null,
    val duration: Long? = null,
    val createdAt: String? = null,
)
