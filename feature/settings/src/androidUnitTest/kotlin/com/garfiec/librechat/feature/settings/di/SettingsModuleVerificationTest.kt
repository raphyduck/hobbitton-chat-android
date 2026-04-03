package com.garfiec.librechat.feature.settings.di

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
                com.garfiec.librechat.core.data.repository.UserRepository::class,
                com.garfiec.librechat.core.data.repository.AuthRepository::class,
                com.garfiec.librechat.core.data.repository.ConversationRepository::class,
                com.garfiec.librechat.core.data.repository.McpRepository::class,
                com.garfiec.librechat.core.data.repository.MemoryRepository::class,
                com.garfiec.librechat.core.data.repository.SpeechRepository::class,
                com.garfiec.librechat.core.data.repository.BalanceRepository::class,
                com.garfiec.librechat.core.data.repository.ShareRepository::class,
                com.garfiec.librechat.core.data.repository.KeyRepository::class,
                com.garfiec.librechat.core.data.repository.ApiKeyRepository::class,
                com.garfiec.librechat.core.data.repository.PresetRepository::class,
                com.garfiec.librechat.core.data.datastore.ThemeDataStore::class,
                com.garfiec.librechat.core.data.datastore.ServerDataStore::class,
                com.garfiec.librechat.core.data.datastore.SettingsDataStore::class,
                com.garfiec.librechat.feature.settings.util.ContentReader::class,
                com.garfiec.librechat.feature.settings.util.PlatformCacheCleaner::class,
                com.garfiec.librechat.feature.settings.viewmodel.delegate.SpeechSettingsFactory::class,
            ),
        )
    }
}
