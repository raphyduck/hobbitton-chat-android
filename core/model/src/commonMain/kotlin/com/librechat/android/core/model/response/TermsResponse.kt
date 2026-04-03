package com.librechat.android.core.model.response

import kotlinx.serialization.Serializable

@Serializable
data class TermsResponse(
    val termsOfService: String? = null,
    val privacyPolicy: String? = null,
)
