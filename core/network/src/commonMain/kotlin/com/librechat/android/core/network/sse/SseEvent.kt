package com.librechat.android.core.network.sse

data class SseEvent(
    val event: String = "",
    val data: String = "",
)
