package com.librechat.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ToolCallResult(
    @SerialName("tool_id") val toolId: String? = null,
    val name: String? = null,
    val output: JsonElement? = null,
    val status: String? = null,
    val error: String? = null,
    val duration: Long? = null,
)
