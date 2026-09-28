package com.garfiec.librechat.feature.tasks.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.feature.tasks.EngineSettingsViewModel
import com.garfiec.librechat.feature.tasks.MissionChatArgs
import com.garfiec.librechat.feature.tasks.MissionChatViewModel
import com.garfiec.librechat.feature.tasks.MissionRunsViewModel
import com.garfiec.librechat.feature.tasks.TasksViewModel
import com.garfiec.librechat.feature.tasks.UsageViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Deliberately NOT added to `sharedKoinModules`: the engine's graph is Android-only for now
 * (D-034), so this module is started alongside `engineModule` from the Android application. On iOS
 * it would resolve a repository nothing provides — a crash at first navigation rather than at
 * startup, which is the worst place to find out.
 */
val tasksModule = module {
    single {
        EngineMissionRepository(
            api = get(),
            scheduler = get(),
            streamClient = get(),
            eventTransport = get(),
            globalProfile = get(),
            kinds = get(),
        )
    }
    viewModelOf(::TasksViewModel)
    viewModelOf(::EngineSettingsViewModel)
    viewModelOf(::UsageViewModel)
    @Suppress("DeprecatedKoinApi")
    viewModel { params -> MissionRunsViewModel(name = params.get(), repository = get()) }
    // The session and its profile arrive from the navigation layer via parametersOf, as one
    // MissionChatArgs, so the lambda-form viewModel is the only DSL that can read them (viewModelOf
    // wires every arg via get()).
    @Suppress("DeprecatedKoinApi")
    viewModel { params ->
        val args = params.get<MissionChatArgs>()
        MissionChatViewModel(
            sessionId = args.sessionId,
            profile = args.profile,
            repository = get(),
            modelPrices = get(),
            settings = get(),
            positions = get(),
            // No transcription (D-077): the only one this composer had was LibreChat's speech route,
            // and LibreChat is gone. The mic and the audio entry disappear with it.
            speech = null,
            ioDispatcher = get(KoinQualifiers.IO),
        )
    }
}
