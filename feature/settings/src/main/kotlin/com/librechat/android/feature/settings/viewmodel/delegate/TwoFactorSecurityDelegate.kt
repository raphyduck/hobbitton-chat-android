package com.librechat.android.feature.settings.viewmodel.delegate

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.data.repository.AuthRepository
import com.librechat.android.feature.settings.viewmodel.SettingsStateHandle
import kotlinx.coroutines.launch

/**
 * Handles 2FA setup, backup codes, and disable flow.
 */
class TwoFactorSecurityDelegate(
    private val stateHandle: SettingsStateHandle,
    private val authRepository: AuthRepository,
) {

    fun toggleTwoFactor() {
        if (stateHandle.state.isTwoFactorEnabled) {
            stateHandle.update { copy(showDisableTwoFactorDialog = true) }
        } else {
            stateHandle.scope.launch {
                stateHandle.update { copy(isTwoFactorLoading = true) }
                when (val result = authRepository.enableTwoFactor()) {
                    is Result.Success -> {
                        stateHandle.update {
                            copy(
                                isTwoFactorLoading = false,
                                showTwoFactorSetupDialog = true,
                                twoFactorOtpauthUrl = result.data.otpauthUrl,
                                backupCodes = result.data.backupCodes,
                            )
                        }
                    }
                    is Result.Error -> {
                        stateHandle.update {
                            copy(
                                isTwoFactorLoading = false,
                                error = result.message ?: "Failed to enable two-factor authentication",
                            )
                        }
                    }
                    is Result.Loading -> { /* no-op */ }
                }
            }
        }
    }

    fun confirmEnableTwoFactor(code: String) {
        stateHandle.scope.launch {
            stateHandle.update { copy(isTwoFactorLoading = true) }
            when (val result = authRepository.confirmTwoFactor(code)) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            isTwoFactorEnabled = true,
                            showTwoFactorSetupDialog = false,
                            twoFactorOtpauthUrl = null,
                            showBackupCodesDialog = true,
                            backupCodes = result.data.backupCodes,
                        )
                    }
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            error = result.message ?: "Invalid verification code",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun confirmDisableTwoFactor(code: String) {
        stateHandle.scope.launch {
            stateHandle.update { copy(isTwoFactorLoading = true) }
            when (val result = authRepository.disableTwoFactor(code)) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            isTwoFactorEnabled = false,
                            showDisableTwoFactorDialog = false,
                        )
                    }
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            error = result.message ?: "Failed to disable two-factor authentication",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun dismissTwoFactorSetupDialog() {
        stateHandle.update { copy(showTwoFactorSetupDialog = false, twoFactorOtpauthUrl = null) }
    }

    fun dismissDisableTwoFactorDialog() {
        stateHandle.update { copy(showDisableTwoFactorDialog = false) }
    }

    fun dismissBackupCodesDialog() {
        stateHandle.update { copy(showBackupCodesDialog = false, backupCodes = emptyList()) }
    }

    fun viewBackupCodes() {
        stateHandle.scope.launch {
            stateHandle.update { copy(isTwoFactorLoading = true) }
            when (val result = authRepository.regenerateBackupCodes()) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            showBackupCodesDialog = true,
                            backupCodes = result.data.backupCodes,
                        )
                    }
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            isTwoFactorLoading = false,
                            error = result.message ?: "Failed to retrieve backup codes",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }
}
