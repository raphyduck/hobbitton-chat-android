package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Preset(
    val presetId: String? = null,
    val title: String? = null,
    val user: String? = null,
    val defaultPreset: Boolean? = null,
    val order: Int? = null,
    val endpoint: EModelEndpoint? = null,
    val endpointType: EModelEndpoint? = null,
    val model: String? = null,
    @SerialName("agent_id") val agentId: String? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    val topK: Int? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val system: String? = null,
    val iconURL: String? = null,
    val greeting: String? = null,
    val stop: List<String>? = null,
    val effort: String? = null,
    @SerialName("thinkingLevel") val thinkingLevel: String? = null,
    @SerialName("web_search") val webSearch: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
