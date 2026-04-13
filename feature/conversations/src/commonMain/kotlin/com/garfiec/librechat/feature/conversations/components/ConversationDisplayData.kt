package com.garfiec.librechat.feature.conversations.components

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EModelEndpoint
import com.garfiec.librechat.core.model.SAVED_TAG

@Immutable
data class ConversationDisplayData(
    val conversationId: String,
    val title: String,
    val endpoint: EModelEndpoint?,
    val model: String?,
    val updatedAt: String?,
    val isBookmarked: Boolean,
)

fun Conversation.toDisplayData() = ConversationDisplayData(
    conversationId = conversationId ?: "",
    title = title ?: "New Chat",
    endpoint = endpoint,
    model = model,
    updatedAt = updatedAt,
    isBookmarked = SAVED_TAG in tags,
)
