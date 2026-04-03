package com.librechat.android.core.model.response

import com.librechat.android.core.model.Message
import kotlinx.serialization.Serializable

@Serializable
data class MessageListResponse(
    val messages: List<Message> = emptyList(),
)
