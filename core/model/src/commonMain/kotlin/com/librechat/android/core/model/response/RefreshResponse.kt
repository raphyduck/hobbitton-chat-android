package com.librechat.android.core.model.response

import com.librechat.android.core.model.User
import kotlinx.serialization.Serializable

@Serializable
data class RefreshResponse(
    val token: String,
    val user: User? = null,
)
