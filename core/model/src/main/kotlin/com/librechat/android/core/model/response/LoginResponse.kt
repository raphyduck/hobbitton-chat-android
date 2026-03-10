package com.librechat.android.core.model.response

import com.librechat.android.core.model.User
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val token: String? = null,
    val user: User? = null,
    val twoFactorRequired: Boolean = false,
    val tempToken: String? = null,
)
