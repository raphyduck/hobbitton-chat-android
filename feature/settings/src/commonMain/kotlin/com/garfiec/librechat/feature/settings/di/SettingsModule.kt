package com.garfiec.librechat.feature.settings.di

import com.garfiec.librechat.feature.settings.viewmodel.ApiKeysViewModel
import com.garfiec.librechat.feature.settings.viewmodel.FavoritesViewModel
import com.garfiec.librechat.feature.settings.viewmodel.McpViewModel
import com.garfiec.librechat.feature.settings.viewmodel.MemoriesViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PresetManagerViewModel
import com.garfiec.librechat.feature.settings.viewmodel.SettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.providerkeys.ProviderKeysViewModel
import com.garfiec.librechat.feature.settings.viewmodel.providerkeys.SetProviderKeyViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

expect val settingsPlatformModule: Module

val settingsModule = module {
    includes(settingsPlatformModule)

    viewModelOf(::SettingsViewModel)
    viewModelOf(::ApiKeysViewModel)
    viewModelOf(::FavoritesViewModel)
    viewModelOf(::MemoriesViewModel)
    viewModelOf(::McpViewModel)
    viewModelOf(::PresetManagerViewModel)
    viewModelOf(::ProviderKeysViewModel)

    // viewModelOf has no overload that accepts ParametersHolder, so the runtime
    // endpointName parameter forces the lambda DSL despite the deprecation hint.
    @Suppress("DeprecatedKoinApi")
    viewModel { params ->
        SetProviderKeyViewModel(
            endpointName = params.get(),
            keyRepository = get(),
            configRepository = get(),
        )
    }
}
