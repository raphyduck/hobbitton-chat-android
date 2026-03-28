package com.librechat.android.feature.settings.di

import com.librechat.android.feature.settings.viewmodel.ApiKeysViewModel
import com.librechat.android.feature.settings.viewmodel.McpViewModel
import com.librechat.android.feature.settings.viewmodel.MemoriesViewModel
import com.librechat.android.feature.settings.viewmodel.PresetManagerViewModel
import com.librechat.android.feature.settings.viewmodel.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val settingsModule = module {
    viewModel {
        SettingsViewModel(
            context = androidContext(),
            userRepository = get(),
            authRepository = get(),
            conversationRepository = get(),
            themeDataStore = get(),
            serverDataStore = get(),
            settingsDataStore = get(),
            mcpRepository = get(),
            memoryRepository = get(),
            speechRepository = get(),
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
