package com.librechat.android.shared

import com.librechat.android.core.common.di.KoinQualifiers
import com.librechat.android.core.common.di.commonModule
import com.librechat.android.core.data.di.dataModule
import com.librechat.android.feature.agents.di.agentsModule
import com.librechat.android.feature.files.di.filesModule
import com.librechat.android.feature.auth.di.authModule
import com.librechat.android.feature.chat.di.chatModule
import com.librechat.android.feature.conversations.di.conversationsModule
import com.librechat.android.feature.settings.di.settingsModule
import com.librechat.android.core.network.api.AgentsApi
import com.librechat.android.core.network.api.ApiKeysApi
import com.librechat.android.core.network.api.AuthApi
import com.librechat.android.core.network.api.BalanceApi
import com.librechat.android.core.network.api.BannerApi
import com.librechat.android.core.network.api.ChatApi
import com.librechat.android.core.network.api.ConfigApi
import com.librechat.android.core.network.api.ConversationsApi
import com.librechat.android.core.network.api.FilesApi
import com.librechat.android.core.network.api.FilesExtApi
import com.librechat.android.core.network.api.KeysApi
import com.librechat.android.core.network.api.McpApi
import com.librechat.android.core.network.api.MemoriesApi
import com.librechat.android.core.network.api.MessagesApi
import com.librechat.android.core.network.api.PresetsApi
import com.librechat.android.core.network.api.PromptsApi
import com.librechat.android.core.network.api.SearchApi
import com.librechat.android.core.network.api.ShareApi
import com.librechat.android.core.network.api.SpeechApi
import com.librechat.android.core.network.api.TagsApi
import com.librechat.android.core.network.api.UserApi
import com.librechat.android.core.network.client.AuthInterceptorPlugin
import com.librechat.android.core.network.client.LibreChatHttpClient
import com.librechat.android.core.network.client.ServerUrlProvider
import com.librechat.android.core.network.client.TokenManager
import com.librechat.android.core.network.sse.SseClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * iOS-specific Koin module providing platform implementations and HTTP clients.
 * Includes dataModule which provides repositories, DAOs, DataStore, and token storage.
 */
val iosSharedModule = module {

    includes(commonModule)
    includes(dataModule)
    includes(authModule)
    includes(chatModule)
    includes(conversationsModule)
    includes(settingsModule)
    includes(agentsModule)
    includes(filesModule)

    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
            explicitNulls = false
            coerceInputValues = true
        }
    }

    // Main HttpClient (with auth interceptor)
    single {
        LibreChatHttpClient.create(
            engineFactory = Darwin,
            json = get(),
            tokenManager = get(),
            serverUrlProvider = get(),
        )
    }

    // Streaming HttpClient (long-lived SSE connections)
    single(KoinQualifiers.Streaming) {
        val tokenManager = get<TokenManager>()
        val serverUrlProvider = get<ServerUrlProvider>()
        HttpClient(Darwin) {
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
                    LibreChatHttpClient.BROWSER_USER_AGENT,
                )
            }
        }
    }

    // Refresh HttpClient (no auth interceptor, short timeout)
    single(KoinQualifiers.Refresh) {
        val json = get<Json>()
        val serverUrlProvider = get<ServerUrlProvider>()
        HttpClient(Darwin) {
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

    // API services (all take HttpClient as constructor parameter)
    singleOf(::AuthApi)
    singleOf(::ChatApi)
    singleOf(::ConfigApi)
    singleOf(::ConversationsApi)
    singleOf(::FilesApi)
    singleOf(::FilesExtApi)
    singleOf(::MessagesApi)
    singleOf(::UserApi)
    singleOf(::AgentsApi)
    singleOf(::PresetsApi)
    singleOf(::PromptsApi)
    singleOf(::TagsApi)
    singleOf(::ShareApi)
    singleOf(::SearchApi)
    singleOf(::BalanceApi)
    singleOf(::BannerApi)
    singleOf(::KeysApi)
    singleOf(::ApiKeysApi)
    singleOf(::McpApi)
    singleOf(::MemoriesApi)
    singleOf(::SpeechApi)
    singleOf(::SseClient)

    // SDK facade
    single {
        LibreChatSDK(
            authApi = get(),
            chatApi = get(),
            sseClient = get(),
            tokenManager = get(),
        )
    }
}
