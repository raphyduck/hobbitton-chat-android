package com.librechat.android.core.model.response

import kotlinx.serialization.Serializable

@Serializable
data class ChatStartResponse(
    val conversationId: String,
)
