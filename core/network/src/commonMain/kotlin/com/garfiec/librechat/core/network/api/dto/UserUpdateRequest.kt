package com.garfiec.librechat.core.network.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserUpdateRequest(
    val name: String? = null,
    val username: String? = null,
)
