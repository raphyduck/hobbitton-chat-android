package com.librechat.android.feature.chat.di

import com.librechat.android.feature.chat.prompts.PromptEditorViewModel
import com.librechat.android.feature.chat.prompts.PromptsViewModel
import com.librechat.android.feature.chat.viewmodel.ChatViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val chatModule = module {
    viewModel {
        ChatViewModel(
            savedStateHandle = get(),
            appContext = androidContext(),
            agentRepository = get(),
            chatRepository = get(),
            messageRepository = get(),
            configRepository = get(),
            conversationRepository = get(),
            draftRepository = get(),
            fileRepository = get(),
            presetRepository = get(),
            promptRepository = get(),
            shareRepository = get(),
            speechRepository = get(),
            mcpRepository = get(),
            userRepository = get(),
            connectivityObserver = get(),
            serverDataStore = get(),
            settingsDataStore = get(),
        )
    }
    viewModelOf(::PromptsViewModel)
    viewModelOf(::PromptEditorViewModel)
}
