package com.garfiec.librechat.core.network.di

import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

/** Binds the platform's HTTP engine; every client of the app is built on it. */
expect val networkPlatformModule: Module

/**
 * The app's one JSON configuration, lenient on what servers add or leave out. Top-level (not
 * inline in the Koin block) so wire-shape tests decode against the shipped instance.
 */
val librechatJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
    explicitNulls = false
    coerceInputValues = true
}

val networkModule = module {
    includes(networkPlatformModule)

    single { librechatJson }
}
