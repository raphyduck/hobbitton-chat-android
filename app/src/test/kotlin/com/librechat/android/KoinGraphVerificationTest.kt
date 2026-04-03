package com.librechat.android

import com.librechat.android.core.common.di.commonModule
import com.librechat.android.core.common.network.ConnectivityObserver
import com.librechat.android.core.data.datastore.ConfigCacheDataStore
import com.librechat.android.core.data.datastore.ServerDataStore
import com.librechat.android.core.data.datastore.SettingsDataStore
import com.librechat.android.core.data.datastore.ThemeDataStore
import com.librechat.android.core.data.di.dataModule
import com.librechat.android.core.data.repository.AgentRepository
import com.librechat.android.core.data.repository.ApiKeyRepository
import com.librechat.android.core.data.repository.AuthRepository
import com.librechat.android.core.data.repository.BalanceRepository
import com.librechat.android.core.data.repository.BannerRepository
import com.librechat.android.core.data.repository.ChatRepository
import com.librechat.android.core.data.repository.ConfigRepository
import com.librechat.android.core.data.repository.ConversationRepository
import com.librechat.android.core.data.repository.DraftRepository
import com.librechat.android.core.data.repository.FileRepository
import com.librechat.android.core.data.repository.KeyRepository
import com.librechat.android.core.data.repository.McpRepository
import com.librechat.android.core.data.repository.MemoryRepository
import com.librechat.android.core.data.repository.MessageRepository
import com.librechat.android.core.data.repository.PresetRepository
import com.librechat.android.core.data.repository.PromptRepository
import com.librechat.android.core.data.repository.SearchRepository
import com.librechat.android.core.data.repository.ShareRepository
import com.librechat.android.core.data.repository.SpeechRepository
import com.librechat.android.core.data.repository.TagRepository
import com.librechat.android.core.data.repository.UserRepository
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
import com.librechat.android.core.network.client.SecureTokenStorage
import com.librechat.android.core.network.client.ServerUrlProvider
import com.librechat.android.core.network.client.TokenManager
import com.librechat.android.core.network.di.networkModule
import com.librechat.android.core.network.sse.SseClient
import com.librechat.android.feature.agents.di.agentsModule
import com.librechat.android.feature.auth.di.authModule
import com.librechat.android.feature.auth.oauth.OAuthLauncher
import com.librechat.android.feature.chat.di.chatModule
import com.librechat.android.feature.conversations.di.conversationsModule
import com.librechat.android.feature.files.di.filesModule
import com.librechat.android.feature.files.platform.FileReader
import com.librechat.android.feature.settings.di.settingsModule
import com.librechat.android.navigation.appModule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import org.koin.core.module.Module
import org.koin.test.verify.verify
import kotlin.reflect.KClass

class KoinGraphVerificationTest {

    private val allModules: List<Module> = listOf(
        commonModule,
        networkModule,
        dataModule,
        appModule,
        authModule,
        chatModule,
        conversationsModule,
        settingsModule,
        agentsModule,
        filesModule,
    )

    /**
     * Integration-level Koin graph verification.
     *
     * Koin's verify() operates per-module and cannot resolve definitions
     * from other modules. This test whitelists all cross-module and
     * framework types so every module is verified in a single test class.
     * If a type is renamed or removed, this test will catch it.
     */
    @OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)
    @Test
    fun verifyFullKoinGraph() {
        val extraTypes = mutableListOf<KClass<*>>(
            // Android framework
            android.content.Context::class,
            android.app.Application::class,
            androidx.lifecycle.SavedStateHandle::class,
            // core:common provides
            CoroutineDispatcher::class,
            CoroutineScope::class,
            ConnectivityObserver::class,
            // core:network provides
            TokenManager::class,
            SecureTokenStorage::class,
            ServerUrlProvider::class,
            SseClient::class,
            AgentsApi::class,
            ApiKeysApi::class,
            AuthApi::class,
            BalanceApi::class,
            BannerApi::class,
            ChatApi::class,
            ConfigApi::class,
            ConversationsApi::class,
            FilesApi::class,
            FilesExtApi::class,
            KeysApi::class,
            McpApi::class,
            MemoriesApi::class,
            MessagesApi::class,
            PresetsApi::class,
            PromptsApi::class,
            SearchApi::class,
            ShareApi::class,
            SpeechApi::class,
            TagsApi::class,
            UserApi::class,
            // core:data provides
            ConfigCacheDataStore::class,
            ServerDataStore::class,
            SettingsDataStore::class,
            ThemeDataStore::class,
            AgentRepository::class,
            ApiKeyRepository::class,
            AuthRepository::class,
            BalanceRepository::class,
            BannerRepository::class,
            ChatRepository::class,
            ConfigRepository::class,
            ConversationRepository::class,
            DraftRepository::class,
            FileRepository::class,
            KeyRepository::class,
            McpRepository::class,
            MemoryRepository::class,
            MessageRepository::class,
            PresetRepository::class,
            PromptRepository::class,
            SearchRepository::class,
            ShareRepository::class,
            SpeechRepository::class,
            TagRepository::class,
            UserRepository::class,
            // feature:auth platform provides
            OAuthLauncher::class,
            // feature:files platform provides
            FileReader::class,
            // Wrappers/DSL types that verify can't resolve via constructor
            kotlin.Lazy::class,
            java.io.File::class,
        )

        // Types whose libraries aren't on the app test classpath (transitive
        // implementation deps). Resolve via reflection at runtime.
        val reflectionTypes = listOf(
            "io.ktor.client.HttpClient",
            "io.ktor.client.engine.HttpClientEngine",
            "io.ktor.client.engine.HttpClientEngineConfig",
            "kotlinx.serialization.json.Json",
            "androidx.datastore.core.DataStore",
        )
        reflectionTypes.forEach { className ->
            extraTypes.add(Class.forName(className).kotlin)
        }

        allModules.forEach { module ->
            module.verify(extraTypes = extraTypes)
        }
    }
}
