package com.librechat.android.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateTagRequest(
    val tag: String,
    val description: String? = null,
    val position: Int? = null,
)

@Serializable
data class UpdateTagRequest(
    val description: String? = null,
    val position: Int? = null,
)
