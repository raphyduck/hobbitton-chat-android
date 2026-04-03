package com.garfiec.librechat.feature.chat.di

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
                com.garfiec.librechat.core.data.repository.AgentRepository::class,
                com.garfiec.librechat.core.data.repository.ChatRepository::class,
                com.garfiec.librechat.core.data.repository.MessageRepository::class,
                com.garfiec.librechat.core.data.repository.ConfigRepository::class,
                com.garfiec.librechat.core.data.repository.ConversationRepository::class,
                com.garfiec.librechat.core.data.repository.DraftRepository::class,
                com.garfiec.librechat.core.data.repository.FileRepository::class,
                com.garfiec.librechat.core.data.repository.PresetRepository::class,
                com.garfiec.librechat.core.data.repository.PromptRepository::class,
                com.garfiec.librechat.core.data.repository.ShareRepository::class,
                com.garfiec.librechat.core.data.repository.SpeechRepository::class,
                com.garfiec.librechat.core.data.repository.McpRepository::class,
                com.garfiec.librechat.core.data.repository.UserRepository::class,
                com.garfiec.librechat.core.common.network.ConnectivityObserver::class,
                com.garfiec.librechat.core.data.datastore.ServerDataStore::class,
                com.garfiec.librechat.core.data.datastore.SettingsDataStore::class,
            ),
        )
    }
}
