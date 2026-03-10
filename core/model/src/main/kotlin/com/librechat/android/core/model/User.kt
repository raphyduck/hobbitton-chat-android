package com.librechat.android.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String? = null,
    @SerialName("_id") val mongoId: String? = null,
    val name: String? = null,
    val username: String? = null,
    val email: String,
    val emailVerified: Boolean = false,
    val avatar: String? = null,
    val provider: String = "local",
    val role: String = "USER",
    val twoFactorEnabled: Boolean = false,
    val termsAccepted: Boolean = false,
    val favorites: List<UserFavorite> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Immutable
@Serializable
data class UserFavorite(
    val agentId: String? = null,
    val model: String? = null,
    val endpoint: String? = null,
)
