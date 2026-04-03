package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class MessageContentPart(
    val type: ContentType,
    val text: String? = null,
    val think: String? = null,
    val error: String? = null,
    @SerialName("tool_call_ids") val toolCallIds: List<String>? = null,
    @SerialName("tool_call") val toolCall: AgentToolCall? = null,
    @SerialName("image_file") val imageFile: ImageFileContent? = null,
    @SerialName("image_url") val imageUrl: ImageUrlContent? = null,
    @SerialName("video_url") val videoUrl: VideoUrlContent? = null,
    @SerialName("input_audio") val inputAudio: InputAudioContent? = null,
    @SerialName("agent_update") val agentUpdate: AgentUpdateContent? = null,
    val agentId: String? = null,
    val groupId: Int? = null,
    val stepIndex: Int? = null,
    val siblingIndex: Int? = null,
)

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

@Serializable
data class FunctionCall(
    val name: String? = null,
    val arguments: String? = null,
    val output: String? = null,
)

@Serializable
data class ImageFileContent(
    @SerialName("file_id") val fileId: String? = null,
    val filepath: String? = null,
    val filename: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class ImageUrlContent(
    val url: String? = null,
    val detail: String? = null,
)

@Serializable
data class VideoUrlContent(
    val url: String? = null,
)

@Serializable
data class InputAudioContent(
    val data: String? = null,
    val format: String? = null,
)

@Serializable
data class AgentUpdateContent(
    val index: Int? = null,
    val runId: String? = null,
    val agentId: String? = null,
)
