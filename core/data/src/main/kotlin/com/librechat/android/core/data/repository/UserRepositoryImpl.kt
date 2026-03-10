package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.result.safeApiCall
import com.librechat.android.core.model.User
import com.librechat.android.core.model.UserFavorite
import com.librechat.android.core.model.request.ResendVerificationRequest
import com.librechat.android.core.model.request.VerifyEmailRequest
import com.librechat.android.core.model.response.TermsResponse
import com.librechat.android.core.network.api.UserApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApi: UserApi,
) : UserRepository {

    override suspend fun getUser(): Result<User> = safeApiCall {
        userApi.getUser()
    }

    override suspend fun deleteUser(): Result<Unit> = safeApiCall {
        userApi.deleteUser()
    }

    override suspend fun uploadAvatar(imageBytes: ByteArray): Result<User> = safeApiCall {
        userApi.updateAvatar(imageBytes)
    }

    override suspend fun verifyEmail(token: String): Result<Unit> = safeApiCall {
        userApi.verifyEmail(VerifyEmailRequest(token = token))
    }

    override suspend fun resendVerification(email: String): Result<Unit> = safeApiCall {
        userApi.resendVerification(ResendVerificationRequest(email = email))
    }

    override suspend fun getTerms(): Result<TermsResponse> = safeApiCall {
        userApi.getTerms()
    }

    override suspend fun acceptTerms(): Result<Unit> = safeApiCall {
        userApi.acceptTerms()
    }

    override suspend fun getFavorites(): Result<List<UserFavorite>> = safeApiCall {
        userApi.getFavorites()
    }

    override suspend fun updateFavorites(favorites: List<UserFavorite>): Result<User> = safeApiCall {
        userApi.updateFavorites(favorites)
    }
}
