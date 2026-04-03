package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.LoginOutcome
import com.librechat.android.core.model.User
import com.librechat.android.core.model.response.TwoFactorSetupResponse

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<LoginOutcome>
    suspend fun loginWithOAuthToken(refreshToken: String): Result<User>
    suspend fun verifyTwoFactor(tempToken: String, code: String): Result<User>
    suspend fun register(name: String, email: String, username: String, password: String): Result<Unit>
    suspend fun logout(): Result<Unit>
    suspend fun isLoggedIn(): Boolean
    suspend fun enableTwoFactor(token: String? = null, backupCode: String? = null): Result<TwoFactorSetupResponse>
    suspend fun confirmTwoFactor(code: String): Result<TwoFactorSetupResponse>
    suspend fun disableTwoFactor(code: String): Result<Unit>
    suspend fun regenerateBackupCodes(token: String? = null, backupCode: String? = null): Result<TwoFactorSetupResponse>
    suspend fun requestPasswordReset(email: String): Result<Unit>
    suspend fun resetPassword(userId: String, token: String, password: String, confirmPassword: String): Result<Unit>
}
