package com.librechat.android.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class FeedbackRequest(
    val feedback: String? = null,
)
