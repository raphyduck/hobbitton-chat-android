package com.librechat.android.core.network.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.util.AttributeKey
import timber.log.Timber

class AuthInterceptorPlugin private constructor(
    private val tokenManager: TokenManager,
) {
    class Config {
        lateinit var tokenManager: TokenManager
    }

    companion object : HttpClientPlugin<Config, AuthInterceptorPlugin> {
        override val key = AttributeKey<AuthInterceptorPlugin>("AuthInterceptor")
        private val RetryFlag = AttributeKey<Boolean>("AuthRetried")

        override fun prepare(block: Config.() -> Unit): AuthInterceptorPlugin {
            val config = Config().apply(block)
            return AuthInterceptorPlugin(config.tokenManager)
        }

        override fun install(plugin: AuthInterceptorPlugin, scope: HttpClient) {
            val skipPaths = setOf(
                "auth/login", "auth/register", "auth/refresh",
                "auth/requestPasswordReset", "auth/resetPassword",
            )

            // Attach token to outgoing requests
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                val path = context.url.buildString()
                if (skipPaths.none { path.contains(it) }) {
                    val token = plugin.tokenManager.getAccessToken()
                    if (token != null) {
                        context.headers.append(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            }

            // Intercept 401 responses at the HttpSend level for proper retry.
            // HttpSend.intercept operates before response deserialization, so retrying
            // here re-runs the full pipeline including content negotiation.
            scope.plugin(HttpSend).intercept { request ->
                val originalCall = execute(request)

                if (originalCall.response.status != HttpStatusCode.Unauthorized) {
                    return@intercept originalCall
                }

                // Check if we already retried this request
                val alreadyRetried = request.attributes.getOrNull(RetryFlag) == true
                if (alreadyRetried) {
                    Timber.w("401 after retry - session expired")
                    plugin.tokenManager.emitSessionExpired()
                    return@intercept originalCall
                }

                Timber.d("401 received, attempting token refresh")
                val refreshed = plugin.tokenManager.refreshAccessToken()
                if (!refreshed) {
                    Timber.w("Token refresh failed - session expired")
                    plugin.tokenManager.emitSessionExpired()
                    return@intercept originalCall
                }

                // Retry the original request with the new token
                val newToken = plugin.tokenManager.getAccessToken()
                request.headers {
                    remove(HttpHeaders.Authorization)
                    if (newToken != null) {
                        append(HttpHeaders.Authorization, "Bearer $newToken")
                    }
                }
                request.attributes.put(RetryFlag, true)

                execute(request)
            }
        }
    }
}
