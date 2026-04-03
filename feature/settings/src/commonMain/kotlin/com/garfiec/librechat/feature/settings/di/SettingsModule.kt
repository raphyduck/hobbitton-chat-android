package com.garfiec.librechat.feature.settings.di

import com.garfiec.librechat.feature.settings.viewmodel.ApiKeysViewModel
import com.garfiec.librechat.feature.settings.viewmodel.McpViewModel
import com.garfiec.librechat.feature.settings.viewmodel.MemoriesViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PresetManagerViewModel
import com.garfiec.librechat.feature.settings.viewmodel.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

expect val settingsPlatformModule: Module

val settingsModule = module {
    includes(settingsPlatformModule)

    viewModel {
        SettingsViewModel(
            contentReader = get(),
            cacheCleaner = get(),
            userRepository = get(),
            authRepository = get(),
            conversationRepository = get(),
            themeDataStore = get(),
            serverDataStore = get(),
            settingsDataStore = get(),
            mcpRepository = get(),
            memoryRepository = get(),
            speechSettingsFactory = get(),
            balanceRepository = get(),
            shareRepository = get(),
            keyRepository = get(),
        )
    }
    viewModelOf(::ApiKeysViewModel)
    viewModelOf(::MemoriesViewModel)
    viewModelOf(::McpViewModel)
    viewModelOf(::PresetManagerViewModel)
}
