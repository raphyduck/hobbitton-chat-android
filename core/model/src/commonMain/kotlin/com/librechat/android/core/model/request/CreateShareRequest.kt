package com.librechat.android.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateShareRequest(
    val targetMessageId: String? = null,
)
