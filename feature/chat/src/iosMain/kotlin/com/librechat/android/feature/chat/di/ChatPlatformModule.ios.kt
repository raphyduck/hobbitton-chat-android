package com.librechat.android.feature.chat.di

import com.librechat.android.feature.chat.viewmodel.ChatViewModel
import com.librechat.android.feature.chat.viewmodel.delegate.IosDelegateFactory
import com.librechat.android.feature.chat.viewmodel.delegate.PlatformDelegateFactory
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

actual val chatPlatformModule: Module = module {
    single<PlatformDelegateFactory> {
        IosDelegateFactory(
            fileRepository = get(),
            speechRepository = get(),
            settingsDataStore = get(),
        )
    }

    viewModel {
        ChatViewModel(
            savedStateHandle = get(),
            agentRepository = get(),
            chatRepository = get(),
            messageRepository = get(),
            configRepository = get(),
            conversationRepository = get(),
            draftRepository = get(),
            presetRepository = get(),
            promptRepository = get(),
            shareRepository = get(),
            mcpRepository = get(),
            userRepository = get(),
            connectivityObserver = get(),
            serverDataStore = get(),
            settingsDataStore = get(),
            platformDelegateFactory = get(),
        )
    }
}
