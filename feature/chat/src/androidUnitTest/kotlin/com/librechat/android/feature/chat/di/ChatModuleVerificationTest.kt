package com.librechat.android.feature.chat.di

import org.junit.Test
import org.koin.test.verify.verify

class ChatModuleVerificationTest {
    @Test
    fun verifyChatModule() {
        chatModule.verify(
            extraTypes = listOf(
                android.content.Context::class,
                android.app.Application::class,
                androidx.lifecycle.SavedStateHandle::class,
                com.librechat.android.core.data.repository.AgentRepository::class,
                com.librechat.android.core.data.repository.ChatRepository::class,
                com.librechat.android.core.data.repository.MessageRepository::class,
                com.librechat.android.core.data.repository.ConfigRepository::class,
                com.librechat.android.core.data.repository.ConversationRepository::class,
                com.librechat.android.core.data.repository.DraftRepository::class,
                com.librechat.android.core.data.repository.FileRepository::class,
                com.librechat.android.core.data.repository.PresetRepository::class,
                com.librechat.android.core.data.repository.PromptRepository::class,
                com.librechat.android.core.data.repository.ShareRepository::class,
                com.librechat.android.core.data.repository.SpeechRepository::class,
                com.librechat.android.core.data.repository.McpRepository::class,
                com.librechat.android.core.data.repository.UserRepository::class,
                com.librechat.android.core.common.network.ConnectivityObserver::class,
                com.librechat.android.core.data.datastore.ServerDataStore::class,
                com.librechat.android.core.data.datastore.SettingsDataStore::class,
            ),
        )
    }
}
