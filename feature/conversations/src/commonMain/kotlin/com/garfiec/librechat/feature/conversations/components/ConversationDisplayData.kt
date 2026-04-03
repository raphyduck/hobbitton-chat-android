package com.garfiec.librechat.feature.conversations.components

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EModelEndpoint

@Immutable
data class ConversationDisplayData(
    val conversationId: String,
    val title: String,
    val endpoint: EModelEndpoint?,
    val model: String?,
    val updatedAt: String?,
    val isBookmarked: Boolean,
)

fun Conversation.toDisplayData(bookmarkedIds: Set<String>) = ConversationDisplayData(
    conversationId = conversationId ?: "",
    title = title ?: "New Chat",
    endpoint = endpoint,
    model = model,
    updatedAt = updatedAt,
    isBookmarked = conversationId in bookmarkedIds,
)
