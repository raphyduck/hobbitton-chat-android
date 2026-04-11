package com.garfiec.librechat.feature.chat.di

import com.garfiec.librechat.feature.chat.prompts.PromptEditorViewModel
import com.garfiec.librechat.feature.chat.prompts.PromptsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val chatModule = module {
    includes(chatPlatformModule)
    viewModelOf(::PromptsViewModel)
    // Koin's constructor-DSL (`viewModelOf`) wires every argument via `get()` and cannot read
    // values passed through `parametersOf`. This VM receives its `initialGroupId` from the
    // navigation layer via `parametersOf`, so the lambda-form `viewModel { params -> ... }` is
    // the only DSL that works here. Detekt's `DeprecatedKoinApi` is a blanket stylistic rule,
    // not a real `@Deprecated` API, so we suppress it in the narrow place it applies.
    @Suppress("DeprecatedKoinApi")
    viewModel { params ->
        PromptEditorViewModel(
            promptRepository = get(),
            initialGroupId = params.getOrNull(),
        )
    }
}

expect val chatPlatformModule: Module
