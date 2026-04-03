package com.librechat.android.feature.conversations.di

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
                com.librechat.android.core.data.repository.ConversationRepository::class,
                com.librechat.android.core.data.repository.MessageRepository::class,
                com.librechat.android.core.data.repository.TagRepository::class,
                com.librechat.android.core.data.repository.ShareRepository::class,
                com.librechat.android.core.data.repository.SearchRepository::class,
                com.librechat.android.core.data.repository.ConfigRepository::class,
                com.librechat.android.core.data.datastore.ServerDataStore::class,
                com.librechat.android.core.data.datastore.SettingsDataStore::class,
            ),
        )
    }
}
