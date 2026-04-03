package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.Message
import kotlinx.serialization.Serializable

@Serializable
data class MessageListResponse(
    val messages: List<Message> = emptyList(),
)
