package com.garfiec.librechat.core.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SaveMessageRequest(
    val messageId: String,
    val parentMessageId: String? = null,
    val responseMessageId: String? = null,
    val overrideParentMessageId: String? = null,
    val sender: String? = null,
    val text: String = "",
    val isCreatedByUser: Boolean = false,
    val model: String? = null,
    val endpoint: String? = null,
    val error: Boolean = false,
    val unfinished: Boolean = false,
    @SerialName("finish_reason") val finishReason: String? = null,
    val metadata: JsonObject? = null,
)
