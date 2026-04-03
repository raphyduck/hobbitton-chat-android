package com.garfiec.librechat.core.network.client

import kotlinx.coroutines.flow.SharedFlow

interface TokenManager {
    /** Non-suspend check — returns true when an access token is cached in memory. */
    val isAuthenticated: Boolean
    suspend fun getAccessToken(): String?
    suspend fun setTokens(accessToken: String, refreshToken: String)
    suspend fun refreshAccessToken(): Boolean
    suspend fun clearTokens()
    fun emitSessionExpired()
    val sessionExpiredFlow: SharedFlow<Unit>
}
