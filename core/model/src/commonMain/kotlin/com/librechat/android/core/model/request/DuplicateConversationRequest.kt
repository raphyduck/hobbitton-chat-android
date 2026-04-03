package com.librechat.android.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class DuplicateConversationRequest(
    val conversationId: String,
    val title: String? = null,
)
