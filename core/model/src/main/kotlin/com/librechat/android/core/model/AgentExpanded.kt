package com.librechat.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class AgentExpanded(
    val id: String,
    @SerialName("_id") val mongoId: String? = null,
    val name: String? = null,
    val description: String? = null,
    val instructions: String? = null,
    val avatar: JsonElement? = null,
    val provider: String? = null,
    val model: String? = null,
    val category: String? = "general",
    val author: String? = null,
    val authorName: String? = null,
    @SerialName("is_promoted") val isPromoted: Boolean = false,
    val isPublic: Boolean? = null,
    @SerialName("conversation_starters") val conversationStarters: List<String> = emptyList(),
    val tools: List<AgentTool> = emptyList(),
    val actions: List<AgentAction> = emptyList(),
    val isCollaborative: Boolean? = null,
    @SerialName("projectIds") val projectIds: List<String> = emptyList(),
    val updatedAt: String? = null,
    val createdAt: String? = null,
) {
    val avatarUrl: String?
        get() = try {
            when (avatar) {
                is JsonObject -> avatar.jsonObject["filepath"]?.jsonPrimitive?.content
                else -> avatar?.jsonPrimitive?.content?.takeIf { it.startsWith("http") }
            }
        } catch (_: Exception) { null }
}
