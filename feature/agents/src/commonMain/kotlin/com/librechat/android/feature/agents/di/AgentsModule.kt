package com.librechat.android.feature.agents.di

import com.librechat.android.feature.agents.viewmodel.AgentDetailViewModel
import com.librechat.android.feature.agents.viewmodel.AgentEditorViewModel
import com.librechat.android.feature.agents.viewmodel.AgentMarketplaceViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

expect val agentsPlatformModule: Module

val agentsModule = module {
    includes(agentsPlatformModule)

    viewModelOf(::AgentMarketplaceViewModel)
    viewModelOf(::AgentDetailViewModel)
    viewModel {
        AgentEditorViewModel(
            savedStateHandle = get(),
            agentRepository = get(),
            configRepository = get(),
            mcpRepository = get(),
            contentReader = get(),
        )
    }
}
