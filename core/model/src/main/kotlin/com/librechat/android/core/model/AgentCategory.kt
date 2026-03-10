package com.librechat.android.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class AgentCategory(
    val value: String,
    val label: String? = null,
    val count: Int = 0,
    val description: String? = null,
)
