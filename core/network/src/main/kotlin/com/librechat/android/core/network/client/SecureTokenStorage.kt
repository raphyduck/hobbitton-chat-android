package com.librechat.android.core.network.client

interface SecureTokenStorage {
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun storeTokens(accessToken: String, refreshToken: String)
    suspend fun clearAll()
}
