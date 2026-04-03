package com.garfiec.librechat.core.network.di

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
                io.ktor.client.engine.HttpClientEngineFactory::class,
                com.garfiec.librechat.core.network.client.TokenManager::class,
                com.garfiec.librechat.core.network.client.ServerUrlProvider::class,
            ),
        )
    }
}
