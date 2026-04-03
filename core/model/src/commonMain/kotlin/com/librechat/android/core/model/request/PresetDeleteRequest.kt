package com.librechat.android.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class PresetDeleteRequest(
    val presetId: String,
)
