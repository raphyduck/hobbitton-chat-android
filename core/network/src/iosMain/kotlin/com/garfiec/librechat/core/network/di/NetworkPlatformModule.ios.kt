package com.garfiec.librechat.core.network.di

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

// NOTE: the iOS `SseHttpTransport` is registered in `IosSharedModule` rather
// than here, because the iOS KMP framework wires its own Koin graph that does
// not `include(networkModule)` — see `shared/src/iosMain/.../IosSharedModule.kt`.
actual val networkPlatformModule: Module = module {
    single<HttpClientEngineFactory<*>> { Darwin }
}
