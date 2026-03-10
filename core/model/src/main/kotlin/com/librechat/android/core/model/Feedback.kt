package com.librechat.android.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Immutable
@Serializable
data class Feedback(
    val rating: FeedbackRating,
    val tag: JsonElement? = null,
    val text: String? = null,
)
