package com.librechat.android.core.model.response

import com.librechat.android.core.model.Conversation
import kotlinx.serialization.Serializable

@Serializable
data class ConversationListResponse(
    val conversations: List<Conversation> = emptyList(),
    val nextCursor: String? = null,
)
