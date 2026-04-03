package com.librechat.android.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdatePromptTagRequest(
    val productionPromptId: String? = null,
)
