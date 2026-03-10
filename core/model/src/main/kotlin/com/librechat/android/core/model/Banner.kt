package com.librechat.android.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Banner(
    val bannerId: String? = null,
    val message: String? = null,
    val displayFrom: String? = null,
    val displayTo: String? = null,
    val type: String? = null,
    val isPublic: Boolean? = null,
    val persistable: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
