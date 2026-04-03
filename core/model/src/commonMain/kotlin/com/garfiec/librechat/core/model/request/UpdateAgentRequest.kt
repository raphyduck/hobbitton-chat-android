package com.garfiec.librechat.core.model.request

import com.garfiec.librechat.core.model.SupportContact
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UpdateAgentRequest(
    val name: String? = null,
    val description: String? = null,
    val instructions: String? = null,
    val model: String? = null,
    val provider: String? = null,
    @SerialName("model_parameters") val modelParameters: JsonObject? = null,
    val artifacts: String? = null,
    @SerialName("recursion_limit") val recursionLimit: Int? = null,
    @SerialName("hide_sequential_outputs") val hideSequentialOutputs: Boolean? = null,
    @SerialName("end_after_tools") val endAfterTools: Boolean? = null,
    val category: String? = null,
    val tools: List<String>? = null,
    @SerialName("conversation_starters") val conversationStarters: List<String>? = null,
    val isPublic: Boolean? = null,
    val isCollaborative: Boolean? = null,
    @SerialName("projectIds") val projectIds: List<String>? = null,
    @SerialName("support_contact") val supportContact: SupportContact? = null,
    @SerialName("tool_options") val toolOptions: JsonObject? = null,
)
