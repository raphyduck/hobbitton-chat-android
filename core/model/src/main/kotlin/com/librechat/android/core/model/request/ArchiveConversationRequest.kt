package com.librechat.android.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class ArchiveConversationRequest(
    val arg: ArchiveConversationArg,
)

@Serializable
data class ArchiveConversationArg(
    val conversationId: String,
    val isArchived: Boolean,
)
