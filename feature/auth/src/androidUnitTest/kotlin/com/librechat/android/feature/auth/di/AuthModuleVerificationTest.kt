package com.librechat.android.feature.auth.di

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
                com.librechat.android.core.data.datastore.ServerDataStore::class,
                com.librechat.android.core.data.repository.AuthRepository::class,
                com.librechat.android.core.data.repository.ConfigRepository::class,
                com.librechat.android.core.data.repository.UserRepository::class,
                com.librechat.android.core.network.client.SecureTokenStorage::class,
                com.librechat.android.feature.auth.oauth.OAuthLauncher::class,
            ),
        )
    }
}
