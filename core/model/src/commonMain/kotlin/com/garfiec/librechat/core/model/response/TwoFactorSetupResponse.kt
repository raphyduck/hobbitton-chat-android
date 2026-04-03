package com.garfiec.librechat.core.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TwoFactorSetupResponse(
    @SerialName("otpauth_url") val otpauthUrl: String,
    @SerialName("backup_codes") val backupCodes: List<String>,
)
