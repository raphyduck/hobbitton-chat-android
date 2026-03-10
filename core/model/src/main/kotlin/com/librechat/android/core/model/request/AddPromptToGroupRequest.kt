package com.librechat.android.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class AddPromptToGroupRequest(
    val prompt: String,
    val type: String,
)
