package com.librechat.android.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.librechat.android.core.common.di.KoinQualifiers
import com.librechat.android.core.data.datastore.ConfigCacheDataStore
import com.librechat.android.core.data.datastore.ServerDataStore
import com.librechat.android.core.data.datastore.SettingsDataStore
import com.librechat.android.core.data.datastore.ThemeDataStore
import com.librechat.android.core.data.datastore.TokenDataStore
import com.librechat.android.core.data.db.LibreChatDatabase
import com.librechat.android.core.data.repository.AgentRepository
import com.librechat.android.core.data.repository.AgentRepositoryImpl
import com.librechat.android.core.data.repository.ApiKeyRepository
import com.librechat.android.core.data.repository.ApiKeyRepositoryImpl
import com.librechat.android.core.data.repository.AuthRepository
import com.librechat.android.core.data.repository.AuthRepositoryImpl
import com.librechat.android.core.data.repository.BalanceRepository
import com.librechat.android.core.data.repository.BalanceRepositoryImpl
import com.librechat.android.core.data.repository.BannerRepository
import com.librechat.android.core.data.repository.BannerRepositoryImpl
import com.librechat.android.core.data.repository.ChatRepository
import com.librechat.android.core.data.repository.ChatRepositoryImpl
import com.librechat.android.core.data.repository.ConfigRepository
import com.librechat.android.core.data.repository.ConfigRepositoryImpl
import com.librechat.android.core.data.repository.ConversationRepository
import com.librechat.android.core.data.repository.ConversationRepositoryImpl
import com.librechat.android.core.data.repository.DraftRepository
import com.librechat.android.core.data.repository.DraftRepositoryImpl
import com.librechat.android.core.data.repository.FileRepository
import com.librechat.android.core.data.repository.FileRepositoryImpl
import com.librechat.android.core.data.repository.KeyRepository
import com.librechat.android.core.data.repository.KeyRepositoryImpl
import com.librechat.android.core.data.repository.McpRepository
import com.librechat.android.core.data.repository.McpRepositoryImpl
import com.librechat.android.core.data.repository.MemoryRepository
import com.librechat.android.core.data.repository.MemoryRepositoryImpl
import com.librechat.android.core.data.repository.MessageRepository
import com.librechat.android.core.data.repository.MessageRepositoryImpl
import com.librechat.android.core.data.repository.PresetRepository
import com.librechat.android.core.data.repository.PresetRepositoryImpl
import com.librechat.android.core.data.repository.PromptRepository
import com.librechat.android.core.data.repository.PromptRepositoryImpl
import com.librechat.android.core.data.repository.SearchRepository
import com.librechat.android.core.data.repository.SearchRepositoryImpl
import com.librechat.android.core.data.repository.ShareRepository
import com.librechat.android.core.data.repository.ShareRepositoryImpl
import com.librechat.android.core.data.repository.SpeechRepository
import com.librechat.android.core.data.repository.SpeechRepositoryImpl
import com.librechat.android.core.data.repository.TagRepository
import com.librechat.android.core.data.repository.TagRepositoryImpl
import com.librechat.android.core.data.repository.UserRepository
import com.librechat.android.core.data.repository.UserRepositoryImpl
import com.librechat.android.core.network.client.SecureTokenStorage
import com.librechat.android.core.network.client.ServerUrlProvider
import com.librechat.android.core.network.client.TokenManager
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "librechat_settings",
)

val dataModule = module {

    // --- Database + DAOs ---

    single {
        Room.databaseBuilder(androidContext(), LibreChatDatabase::class.java, "librechat.db").build()
    }
    single { get<LibreChatDatabase>().conversationDao() }
    single { get<LibreChatDatabase>().messageDao() }
    single { get<LibreChatDatabase>().fileDao() }
    single { get<LibreChatDatabase>().agentDao() }
    single { get<LibreChatDatabase>().presetDao() }
    single { get<LibreChatDatabase>().conversationTagDao() }
    single { get<LibreChatDatabase>().draftDao() }

    // --- DataStore ---

    single<DataStore<Preferences>> { androidContext().settingsDataStore }

    // --- Datastores ---

    single {
        TokenDataStore(
            context = androidContext(),
            refreshClient = lazy(LazyThreadSafetyMode.NONE) { get<HttpClient>(KoinQualifiers.Refresh) },
        )
    } binds arrayOf(TokenManager::class, SecureTokenStorage::class)

    singleOf(::ServerDataStore) bind ServerUrlProvider::class
    singleOf(::ThemeDataStore)
    singleOf(::ConfigCacheDataStore)
    singleOf(::SettingsDataStore)

    // --- Repositories (special wiring) ---

    single<AuthRepository> {
        AuthRepositoryImpl(
            authApi = get(),
            userApi = get(),
            tokenManager = get(),
            context = androidContext(),
        )
    }

    single<ChatRepository> {
        ChatRepositoryImpl(
            chatApi = get(),
            sseClient = get(),
            sseHttpClient = get(KoinQualifiers.Streaming),
            connectivityObserver = get(),
        )
    }

    single<DraftRepository> {
        DraftRepositoryImpl(
            draftDao = get(),
            ioDispatcher = get(KoinQualifiers.IO),
        )
    }

    // --- Repositories (simple auto-wiring) ---

    singleOf(::BalanceRepositoryImpl) bind BalanceRepository::class
    singleOf(::ConfigRepositoryImpl) bind ConfigRepository::class
    singleOf(::ConversationRepositoryImpl) bind ConversationRepository::class
    singleOf(::MessageRepositoryImpl) bind MessageRepository::class
    singleOf(::FileRepositoryImpl) bind FileRepository::class
    singleOf(::AgentRepositoryImpl) bind AgentRepository::class
    singleOf(::TagRepositoryImpl) bind TagRepository::class
    singleOf(::SearchRepositoryImpl) bind SearchRepository::class
    singleOf(::KeyRepositoryImpl) bind KeyRepository::class
    singleOf(::ApiKeyRepositoryImpl) bind ApiKeyRepository::class
    singleOf(::McpRepositoryImpl) bind McpRepository::class
    singleOf(::MemoryRepositoryImpl) bind MemoryRepository::class
    singleOf(::PresetRepositoryImpl) bind PresetRepository::class
    singleOf(::PromptRepositoryImpl) bind PromptRepository::class
    singleOf(::ShareRepositoryImpl) bind ShareRepository::class
    singleOf(::SpeechRepositoryImpl) bind SpeechRepository::class
    singleOf(::UserRepositoryImpl) bind UserRepository::class
    singleOf(::BannerRepositoryImpl) bind BannerRepository::class
}
