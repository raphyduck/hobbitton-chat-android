package com.librechat.android.core.model

data class ServerConnection(
    val url: String,
    val name: String,
    val isDefault: Boolean = false,
)
