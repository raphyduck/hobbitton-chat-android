package com.garfiec.librechat.feature.settings

import androidx.compose.runtime.Immutable

@Immutable
data class UserDisplayData(
    val name: String,
    val email: String,
    val username: String,
    val avatar: String?,
)

@Immutable
data class SharedLinkDisplayData(
    val shareId: String,
    val title: String,
    val createdAt: String?,
    val isPublic: Boolean,
)
