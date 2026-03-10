package com.librechat.android.core.model.response

import com.librechat.android.core.model.User
import kotlinx.serialization.Serializable

@Serializable
data class RegisterResponse(
    val user: User,
)
