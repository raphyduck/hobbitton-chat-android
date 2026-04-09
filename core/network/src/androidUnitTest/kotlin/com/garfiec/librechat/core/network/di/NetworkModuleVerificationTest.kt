package com.garfiec.librechat.core.network.di

import android.app.Application
import android.content.Context
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import com.garfiec.librechat.core.network.client.TokenManager
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import org.junit.Test
import org.koin.test.verify.verify

class NetworkModuleVerificationTest {
    @Test
    fun verifyNetworkModule() {
        networkModule.verify(
            extraTypes = listOf(
                Context::class,
                Application::class,
                HttpClientEngine::class,
                HttpClientEngineFactory::class,
                TokenManager::class,
                ServerUrlProvider::class,
            ),
        )
    }
}
