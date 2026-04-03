package com.librechat.android.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateArtifactRequest(
    val content: String,
)
