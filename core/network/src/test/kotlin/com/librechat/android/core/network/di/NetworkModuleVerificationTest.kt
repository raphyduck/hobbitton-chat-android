package com.librechat.android.core.network.di

import org.junit.Test
import org.koin.test.verify.verify

class NetworkModuleVerificationTest {
    @Test
    fun verifyNetworkModule() {
        networkModule.verify(
            extraTypes = listOf(
                android.content.Context::class,
                android.app.Application::class,
                io.ktor.client.engine.HttpClientEngine::class,
                com.librechat.android.core.network.client.TokenManager::class,
                com.librechat.android.core.network.client.ServerUrlProvider::class,
            ),
        )
    }
}
