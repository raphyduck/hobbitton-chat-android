package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.User
import kotlinx.serialization.Serializable

@Serializable
data class RegisterResponse(
    val user: User,
)
