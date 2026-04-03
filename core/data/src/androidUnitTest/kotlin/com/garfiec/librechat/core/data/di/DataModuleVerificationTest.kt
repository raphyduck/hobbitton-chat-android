package com.garfiec.librechat.core.data.di

import org.junit.Test
import org.koin.test.verify.verify

class DataModuleVerificationTest {
    @Test
    fun verifyDataModule() {
        dataModule.verify(
            extraTypes = listOf(
                android.content.Context::class,
                android.app.Application::class,
                androidx.lifecycle.SavedStateHandle::class,
                kotlin.Lazy::class,
                io.ktor.client.HttpClient::class,
                kotlinx.serialization.json.Json::class,
                kotlinx.coroutines.CoroutineDispatcher::class,
                com.garfiec.librechat.core.common.network.ConnectivityObserver::class,
                com.garfiec.librechat.core.network.sse.SseClient::class,
                com.garfiec.librechat.core.network.api.AuthApi::class,
                com.garfiec.librechat.core.network.api.UserApi::class,
                com.garfiec.librechat.core.network.api.ChatApi::class,
                com.garfiec.librechat.core.network.api.ConversationsApi::class,
                com.garfiec.librechat.core.network.api.MessagesApi::class,
                com.garfiec.librechat.core.network.api.FilesApi::class,
                com.garfiec.librechat.core.network.api.FilesExtApi::class,
                com.garfiec.librechat.core.network.api.AgentsApi::class,
                com.garfiec.librechat.core.network.api.PresetsApi::class,
                com.garfiec.librechat.core.network.api.PromptsApi::class,
                com.garfiec.librechat.core.network.api.TagsApi::class,
                com.garfiec.librechat.core.network.api.ShareApi::class,
                com.garfiec.librechat.core.network.api.ConfigApi::class,
                com.garfiec.librechat.core.network.api.BalanceApi::class,
                com.garfiec.librechat.core.network.api.SearchApi::class,
                com.garfiec.librechat.core.network.api.KeysApi::class,
                com.garfiec.librechat.core.network.api.ApiKeysApi::class,
                com.garfiec.librechat.core.network.api.McpApi::class,
                com.garfiec.librechat.core.network.api.MemoriesApi::class,
                com.garfiec.librechat.core.network.api.SpeechApi::class,
                com.garfiec.librechat.core.network.api.BannerApi::class,
                androidx.datastore.core.DataStore::class,
            ),
        )
    }
}
