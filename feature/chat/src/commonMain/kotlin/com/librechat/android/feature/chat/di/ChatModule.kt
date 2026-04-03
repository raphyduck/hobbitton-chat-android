package com.librechat.android.feature.chat.di

import com.librechat.android.feature.chat.prompts.PromptEditorViewModel
import com.librechat.android.feature.chat.prompts.PromptsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val chatModule = module {
    includes(chatPlatformModule)
    viewModelOf(::PromptsViewModel)
    viewModelOf(::PromptEditorViewModel)
}

expect val chatPlatformModule: Module
