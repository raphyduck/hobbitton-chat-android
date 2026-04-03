package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

@Serializable
data class ShareLinkCheckResponse(
    val success: Boolean,
    val shareId: String? = null,
    val conversationId: String? = null,
)
