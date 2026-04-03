package com.librechat.android.core.model

import kotlinx.serialization.Serializable

@Serializable
data class UserKey(
    val name: String,
    val value: String? = null,
    val expiresAt: String? = null,
)
