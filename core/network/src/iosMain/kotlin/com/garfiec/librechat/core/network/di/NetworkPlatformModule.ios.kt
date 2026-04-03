package com.garfiec.librechat.core.network.di

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

actual val networkPlatformModule: Module = module {
    single<HttpClientEngineFactory<*>> { Darwin }
}
