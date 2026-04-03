package com.garfiec.librechat.feature.auth.di

import org.junit.Test
import org.koin.test.verify.verify

class AuthModuleVerificationTest {
    @Test
    fun verifyAuthModule() {
        authModule.verify(
            extraTypes = listOf(
                android.content.Context::class,
                android.app.Application::class,
                androidx.lifecycle.SavedStateHandle::class,
                com.garfiec.librechat.core.data.datastore.ServerDataStore::class,
                com.garfiec.librechat.core.data.repository.AuthRepository::class,
                com.garfiec.librechat.core.data.repository.ConfigRepository::class,
                com.garfiec.librechat.core.data.repository.UserRepository::class,
                com.garfiec.librechat.core.network.client.SecureTokenStorage::class,
                com.garfiec.librechat.feature.auth.oauth.OAuthLauncher::class,
            ),
        )
    }
}
