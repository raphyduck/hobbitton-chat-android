package com.librechat.android.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class RevertAgentRequest(
    val version: Int,
)
