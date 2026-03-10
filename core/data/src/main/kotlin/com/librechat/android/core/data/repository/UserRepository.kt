package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.User
import com.librechat.android.core.model.UserFavorite
import com.librechat.android.core.model.response.TermsResponse

interface UserRepository {
    suspend fun getUser(): Result<User>
    suspend fun deleteUser(): Result<Unit>
    suspend fun uploadAvatar(imageBytes: ByteArray): Result<User>
    suspend fun verifyEmail(token: String): Result<Unit>
    suspend fun resendVerification(email: String): Result<Unit>
    suspend fun getTerms(): Result<TermsResponse>
    suspend fun acceptTerms(): Result<Unit>
    suspend fun getFavorites(): Result<List<UserFavorite>>
    suspend fun updateFavorites(favorites: List<UserFavorite>): Result<User>
}
