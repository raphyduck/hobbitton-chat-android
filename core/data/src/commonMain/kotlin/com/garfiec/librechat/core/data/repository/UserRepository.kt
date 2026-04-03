package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.UserFavorite
import com.garfiec.librechat.core.model.response.TermsResponse

interface UserRepository {
    suspend fun getUser(): Result<User>
    suspend fun deleteUser(token: String? = null, backupCode: String? = null): Result<Unit>
    suspend fun uploadAvatar(imageBytes: ByteArray): Result<User>
    suspend fun verifyEmail(token: String): Result<Unit>
    suspend fun resendVerification(email: String): Result<Unit>
    suspend fun getTerms(): Result<TermsResponse>
    suspend fun acceptTerms(): Result<Unit>
    suspend fun getFavorites(): Result<List<UserFavorite>>
    suspend fun updateFavorites(favorites: List<UserFavorite>): Result<User>
}
