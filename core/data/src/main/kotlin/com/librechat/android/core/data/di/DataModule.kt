package com.librechat.android.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.librechat.android.core.data.datastore.ServerDataStore
import com.librechat.android.core.data.datastore.TokenDataStore
import com.librechat.android.core.data.repository.AgentRepository
import com.librechat.android.core.data.repository.AgentRepositoryImpl
import com.librechat.android.core.data.repository.ApiKeyRepository
import com.librechat.android.core.data.repository.ApiKeyRepositoryImpl
import com.librechat.android.core.data.repository.AuthRepository
import com.librechat.android.core.data.repository.BalanceRepository
import com.librechat.android.core.data.repository.BalanceRepositoryImpl
import com.librechat.android.core.data.repository.BannerRepository
import com.librechat.android.core.data.repository.BannerRepositoryImpl
import com.librechat.android.core.data.repository.SearchRepository
import com.librechat.android.core.data.repository.SearchRepositoryImpl
import com.librechat.android.core.data.repository.TagRepository
import com.librechat.android.core.data.repository.TagRepositoryImpl
import com.librechat.android.core.data.repository.AuthRepositoryImpl
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
import com.librechat.android.core.data.repository.ShareRepository
import com.librechat.android.core.data.repository.ShareRepositoryImpl
import com.librechat.android.core.data.repository.SpeechRepository
import com.librechat.android.core.data.repository.SpeechRepositoryImpl
import com.librechat.android.core.data.repository.UserRepository
import com.librechat.android.core.data.repository.UserRepositoryImpl
import com.librechat.android.core.network.client.SecureTokenStorage
import com.librechat.android.core.network.client.ServerUrlProvider
import com.librechat.android.core.network.client.TokenManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "librechat_settings",
)

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindTokenManager(impl: TokenDataStore): TokenManager

    @Binds
    @Singleton
    abstract fun bindSecureTokenStorage(impl: TokenDataStore): SecureTokenStorage

    @Binds
    @Singleton
    abstract fun bindServerUrlProvider(impl: ServerDataStore): ServerUrlProvider

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindBalanceRepository(impl: BalanceRepositoryImpl): BalanceRepository

    @Binds
    @Singleton
    abstract fun bindConfigRepository(impl: ConfigRepositoryImpl): ConfigRepository

    @Binds
    @Singleton
    abstract fun bindConversationRepository(impl: ConversationRepositoryImpl): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindDraftRepository(impl: DraftRepositoryImpl): DraftRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindAgentRepository(impl: AgentRepositoryImpl): AgentRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindFileRepository(impl: FileRepositoryImpl): FileRepository

    @Binds
    @Singleton
    abstract fun bindKeyRepository(impl: KeyRepositoryImpl): KeyRepository

    @Binds
    @Singleton
    abstract fun bindApiKeyRepository(impl: ApiKeyRepositoryImpl): ApiKeyRepository

    @Binds
    @Singleton
    abstract fun bindMcpRepository(impl: McpRepositoryImpl): McpRepository

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(impl: MemoryRepositoryImpl): MemoryRepository

    @Binds
    @Singleton
    abstract fun bindPresetRepository(impl: PresetRepositoryImpl): PresetRepository

    @Binds
    @Singleton
    abstract fun bindPromptRepository(impl: PromptRepositoryImpl): PromptRepository

    @Binds
    @Singleton
    abstract fun bindShareRepository(impl: ShareRepositoryImpl): ShareRepository

    @Binds
    @Singleton
    abstract fun bindSpeechRepository(impl: SpeechRepositoryImpl): SpeechRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindBannerRepository(impl: BannerRepositoryImpl): BannerRepository

    companion object {
        @Provides
        @Singleton
        fun provideDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = context.settingsDataStore
    }
}
