package com.garfiec.librechat.feature.tasks.di

import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.feature.tasks.EngineSettingsViewModel
import com.garfiec.librechat.feature.tasks.TasksViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Deliberately NOT added to `sharedKoinModules`: the engine's graph is Android-only for now
 * (D-034), so this module is started alongside `engineModule` from the Android application. On iOS
 * it would resolve a repository nothing provides — a crash at first navigation rather than at
 * startup, which is the worst place to find out.
 */
val tasksModule = module {
    single { EngineMissionRepository(api = get()) }
    viewModelOf(::TasksViewModel)
    viewModelOf(::EngineSettingsViewModel)
}
