package com.librechat.android.core.data.di

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
                com.librechat.android.core.common.network.ConnectivityObserver::class,
                com.librechat.android.core.network.sse.SseClient::class,
                com.librechat.android.core.network.api.AuthApi::class,
                com.librechat.android.core.network.api.UserApi::class,
                com.librechat.android.core.network.api.ChatApi::class,
                com.librechat.android.core.network.api.ConversationsApi::class,
                com.librechat.android.core.network.api.MessagesApi::class,
                com.librechat.android.core.network.api.FilesApi::class,
                com.librechat.android.core.network.api.FilesExtApi::class,
                com.librechat.android.core.network.api.AgentsApi::class,
                com.librechat.android.core.network.api.PresetsApi::class,
                com.librechat.android.core.network.api.PromptsApi::class,
                com.librechat.android.core.network.api.TagsApi::class,
                com.librechat.android.core.network.api.ShareApi::class,
                com.librechat.android.core.network.api.ConfigApi::class,
                com.librechat.android.core.network.api.BalanceApi::class,
                com.librechat.android.core.network.api.SearchApi::class,
                com.librechat.android.core.network.api.KeysApi::class,
                com.librechat.android.core.network.api.ApiKeysApi::class,
                com.librechat.android.core.network.api.McpApi::class,
                com.librechat.android.core.network.api.MemoriesApi::class,
                com.librechat.android.core.network.api.SpeechApi::class,
                com.librechat.android.core.network.api.BannerApi::class,
                androidx.datastore.core.DataStore::class,
            ),
        )
    }
}
