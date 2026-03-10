package com.librechat.android.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class ChatAbortRequest(
    val abortKey: String,
    val endpoint: String,
)
