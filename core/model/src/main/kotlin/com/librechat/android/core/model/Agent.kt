package com.librechat.android.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Immutable
@Serializable
data class Agent(
    val id: String,
    @SerialName("_id") val mongoId: String? = null,
    val name: String? = null,
    val description: String? = null,
    val instructions: String? = null,
    val avatar: JsonElement? = null,
    val provider: String? = null,
    val model: String? = null,
    @SerialName("model_parameters") val modelParameters: JsonElement? = null,
    val artifacts: String? = null,
    @SerialName("access_level") val accessLevel: Int? = null,
    @SerialName("recursion_limit") val recursionLimit: Int? = null,
    @SerialName("hide_sequential_outputs") val hideSequentialOutputs: Boolean? = null,
    @SerialName("end_after_tools") val endAfterTools: Boolean? = null,
    val category: String? = "general",
    val author: String? = null,
    val authorName: String? = null,
    @SerialName("is_promoted") val isPromoted: Boolean = false,
    val isPublic: Boolean? = null,
    @SerialName("conversation_starters") val conversationStarters: List<String> = emptyList(),
    val tools: List<String>? = null,
    val actions: List<String>? = null,
    @SerialName("agent_ids") val agentIds: List<String>? = null,
    val edges: List<JsonElement>? = null,
    val isCollaborative: Boolean? = null,
    @SerialName("projectIds") val projectIds: List<String> = emptyList(),
    val updatedAt: String? = null,
    val createdAt: String? = null,
    @SerialName("support_contact") val supportContact: JsonElement? = null,
    @SerialName("tool_options") val toolOptions: JsonObject? = null,
    val mcpServerNames: List<String>? = null,
) {
    val avatarUrl: String?
        get() = try {
            when (avatar) {
                is JsonObject -> avatar.jsonObject["filepath"]?.jsonPrimitive?.content
                else -> avatar?.jsonPrimitive?.content?.takeIf { it.startsWith("http") }
            }
        } catch (_: Exception) { null }
}
