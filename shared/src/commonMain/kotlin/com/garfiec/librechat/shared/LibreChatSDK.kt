package com.garfiec.librechat.shared

import com.garfiec.librechat.core.network.api.AuthApi
import com.garfiec.librechat.core.network.api.ChatApi
import com.garfiec.librechat.core.network.api.LoginResult
import com.garfiec.librechat.core.network.client.TokenManager
import com.garfiec.librechat.core.network.sse.SseClient

/**
 * Top-level entry point that iOS uses to interact with the KMP business logic.
 * Aggregates the key APIs needed for login + SSE streaming.
 *
 * SSE usage: call `sseClient.connect(streamingHttpClient, streamPath)` directly.
 * SKIE converts the returned `Flow<StreamEvent>` to `AsyncSequence` on iOS.
 */
class LibreChatSDK(
    val authApi: AuthApi,
    val chatApi: ChatApi,
    val sseClient: SseClient,
    val tokenManager: TokenManager,
) {

    /**
     * Perform login and store tokens in one call.
     * On iOS, SKIE converts this suspend fun to async throws.
     */
    @Throws(Exception::class)
    suspend fun login(email: String, password: String): LoginResult {
        val result = authApi.login(email, password)
        val accessToken = result.response.token
        val refreshToken = result.refreshToken
        if (accessToken != null && refreshToken != null) {
            tokenManager.setTokens(accessToken, refreshToken)
        }
        return result
    }
}
