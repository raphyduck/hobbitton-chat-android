package com.garfiec.librechat.feature.conversations.di

import org.junit.Test
import org.koin.test.verify.verify

class ConversationsModuleVerificationTest {
    @Test
    fun verifyConversationsModule() {
        conversationsModule.verify(
            extraTypes = listOf(
                android.content.Context::class,
                android.app.Application::class,
                androidx.lifecycle.SavedStateHandle::class,
                com.garfiec.librechat.core.data.repository.ConversationRepository::class,
                com.garfiec.librechat.core.data.repository.MessageRepository::class,
                com.garfiec.librechat.core.data.repository.TagRepository::class,
                com.garfiec.librechat.core.data.repository.ShareRepository::class,
                com.garfiec.librechat.core.data.repository.SearchRepository::class,
                com.garfiec.librechat.core.data.repository.ConfigRepository::class,
                com.garfiec.librechat.core.data.datastore.ServerDataStore::class,
                com.garfiec.librechat.core.data.datastore.SettingsDataStore::class,
            ),
        )
    }
}
