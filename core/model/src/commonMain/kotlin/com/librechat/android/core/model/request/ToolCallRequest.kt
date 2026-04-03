package com.librechat.android.core.model.request

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ToolCallRequest(
    val input: JsonElement? = null,
)
