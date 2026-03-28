package com.librechat.android.core.network.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Cache
import timber.log.Timber
import java.io.File

object LibreChatHttpClient {

    private const val HTTP_CACHE_SIZE = 50L * 1024 * 1024 // 50 MB

    fun create(
        json: Json,
        tokenManager: TokenManager,
        serverUrlProvider: ServerUrlProvider,
        cacheDir: File? = null,
        debug: Boolean = false,
    ): HttpClient = HttpClient(OkHttp) {
        engine {
            if (cacheDir != null) {
                config {
                    cache(Cache(File(cacheDir, "http_cache"), HTTP_CACHE_SIZE))
                }
            }
        }

        install(ContentNegotiation) {
            json(json)
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    val sanitized = message
                        .replace(Regex("Authorization: Bearer [^\\s]+"), "Authorization: Bearer [REDACTED]")
                        .replace(Regex("refreshToken=[^;&\\s]+"), "refreshToken=[REDACTED]")
                    Timber.tag("HTTP").d(sanitized)
                }
            }
            level = if (debug) LogLevel.HEADERS else LogLevel.NONE
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 120_000
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            retryOnException(maxRetries = 2, retryOnTimeout = true)
            exponentialDelay()
        }

        install(AuthInterceptorPlugin) {
            this.tokenManager = tokenManager
        }

        // Validate responses before deserialization — catches non-2xx and parses error body.
        // Note: 401 responses are handled by AuthInterceptorPlugin at the HttpSend level
        // (token refresh + retry). By the time the validator sees the response, 401 means
        // the retry also failed, so we throw ApiException to surface the auth failure.
        HttpResponseValidator {
            validateResponse { response ->
                if (!response.status.isSuccess()) {
                    val statusCode = response.status.value
                    val bodyText = try { response.bodyAsText() } catch (_: Exception) { "" }
                    val errorMessage = extractErrorMessage(json, bodyText, statusCode)
                    val isBanned = statusCode == 403 && bodyText.contains("ban", ignoreCase = true)

                    Timber.w("HTTP $statusCode: $errorMessage")

                    if (isBanned) {
                        tokenManager.emitSessionExpired()
                    }

                    throw ApiException(
                        statusCode = statusCode,
                        message = errorMessage,
                        isBanned = isBanned,
                    )
                }
            }
        }

        defaultRequest {
            val baseUrl = serverUrlProvider.getBaseUrl()
            if (baseUrl.isNotEmpty()) {
                url.takeFrom(baseUrl)
            }
            contentType(ContentType.Application.Json)
            // WORKAROUND: The backend's uaParser middleware (ua-parser-js) rejects requests
            // that don't have a recognized browser User-Agent with 403. We set a standard
            // Chrome mobile UA so the parser detects a browser name. This is a temporary
            // workaround until the backend exempts native app clients from the UA check.
            headers.append(HttpHeaders.UserAgent, BROWSER_USER_AGENT)
        }
    }

    // Standard Chrome on Android UA string — ua-parser-js will detect "Chrome" as browser name
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    /**
     * Try to extract a user-friendly error message from the response body.
     * Handles JSON responses like {"error": "message"} or {"message": "..."}.
     * Falls back to a generic message based on status code.
     */
    private fun extractErrorMessage(json: Json, body: String, statusCode: Int): String {
        if (body.isNotBlank()) {
            try {
                val jsonObj = json.parseToJsonElement(body).jsonObject
                // Try common error fields: "message", "error", "error_message".
                // Fields may be objects rather than strings, so safe-cast to JsonPrimitive.
                val msg = (jsonObj["message"] as? JsonPrimitive)?.content
                    ?: (jsonObj["error"] as? JsonPrimitive)?.content
                    ?: (jsonObj["error_message"] as? JsonPrimitive)?.content
                if (!msg.isNullOrBlank()) return msg
            } catch (_: Exception) {
                // Body is not JSON (might be HTML) — check for common patterns
                if (body.contains("banned", ignoreCase = true) || body.contains("forbidden", ignoreCase = true)) {
                    return "Access denied. Your account may have been restricted."
                }
            }
        }

        return when (statusCode) {
            400 -> "Bad request"
            403 -> "Access denied"
            404 -> "Not found"
            429 -> "Too many requests. Please try again later."
            in 500..599 -> "Server error. Please try again later."
            else -> "Request failed (HTTP $statusCode)"
        }
    }
}
