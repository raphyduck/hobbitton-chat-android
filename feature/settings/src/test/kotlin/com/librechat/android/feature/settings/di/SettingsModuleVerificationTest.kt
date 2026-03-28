package com.librechat.android.feature.settings.di

import org.junit.Test
import org.koin.test.verify.verify

class SettingsModuleVerificationTest {
    @Test
    fun verifySettingsModule() {
        settingsModule.verify(
            extraTypes = listOf(
                android.content.Context::class,
                android.app.Application::class,
                androidx.lifecycle.SavedStateHandle::class,
                com.librechat.android.core.data.repository.UserRepository::class,
                com.librechat.android.core.data.repository.AuthRepository::class,
                com.librechat.android.core.data.repository.ConversationRepository::class,
                com.librechat.android.core.data.repository.McpRepository::class,
                com.librechat.android.core.data.repository.MemoryRepository::class,
                com.librechat.android.core.data.repository.SpeechRepository::class,
                com.librechat.android.core.data.repository.BalanceRepository::class,
                com.librechat.android.core.data.repository.ShareRepository::class,
                com.librechat.android.core.data.repository.KeyRepository::class,
                com.librechat.android.core.data.repository.ApiKeyRepository::class,
                com.librechat.android.core.data.repository.PresetRepository::class,
                com.librechat.android.core.data.datastore.ThemeDataStore::class,
                com.librechat.android.core.data.datastore.ServerDataStore::class,
                com.librechat.android.core.data.datastore.SettingsDataStore::class,
            ),
        )
    }
}
