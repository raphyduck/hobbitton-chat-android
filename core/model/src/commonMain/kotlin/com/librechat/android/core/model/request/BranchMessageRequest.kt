package com.librechat.android.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class BranchMessageRequest(
    val conversationId: String,
    val messageId: String,
    val agentId: String? = null,
)
