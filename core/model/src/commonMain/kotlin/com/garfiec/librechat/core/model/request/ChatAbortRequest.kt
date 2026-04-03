package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class ChatAbortRequest(
    val abortKey: String,
    val endpoint: String,
)
