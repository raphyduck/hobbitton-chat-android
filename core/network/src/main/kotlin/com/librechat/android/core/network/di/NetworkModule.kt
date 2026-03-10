package com.librechat.android.core.network.di

import android.content.Context
import com.librechat.android.core.network.client.LibreChatHttpClient
import com.librechat.android.core.network.client.ServerUrlProvider
import com.librechat.android.core.network.client.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.librechat.android.core.network.client.AuthInterceptorPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideHttpClient(
        @ApplicationContext context: Context,
        json: Json,
        tokenManager: TokenManager,
        serverUrlProvider: ServerUrlProvider,
    ): HttpClient = LibreChatHttpClient.create(
        json = json,
        tokenManager = tokenManager,
        serverUrlProvider = serverUrlProvider,
        cacheDir = context.cacheDir,
    )

    /**
     * Minimal HttpClient for SSE streaming. No ContentNegotiation (avoids
     * response body buffering), infinite request/socket timeouts (streams
     * can run for minutes), and no response validator (SSE errors are
     * handled by SseClient's retry logic).
     */
    @Provides
    @Singleton
    @StreamingClient
    fun provideSseClient(
        tokenManager: TokenManager,
        serverUrlProvider: ServerUrlProvider,
    ): HttpClient = HttpClient(OkHttp) {
        install(AuthInterceptorPlugin) {
            this.tokenManager = tokenManager
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = Long.MAX_VALUE
            socketTimeoutMillis = Long.MAX_VALUE
        }
        defaultRequest {
            val baseUrl = serverUrlProvider.getBaseUrl()
            if (baseUrl.isNotEmpty()) {
                url.takeFrom(baseUrl)
            }
            headers.append(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
            )
        }
    }

    @Provides
    @Singleton
    @RefreshClient
    fun provideRefreshClient(
        json: Json,
        serverUrlProvider: ServerUrlProvider,
    ): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
        defaultRequest {
            val baseUrl = serverUrlProvider.getBaseUrl()
            if (baseUrl.isNotEmpty()) {
                url.takeFrom(baseUrl)
            }
            contentType(ContentType.Application.Json)
        }
    }
}
