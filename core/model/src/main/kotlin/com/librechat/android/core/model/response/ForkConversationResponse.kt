package com.librechat.android.core.model.response

import com.librechat.android.core.model.Conversation
import com.librechat.android.core.model.Message
import kotlinx.serialization.Serializable

@Serializable
data class ForkConversationResponse(
    val conversation: Conversation,
    val messages: List<Message> = emptyList(),
)
