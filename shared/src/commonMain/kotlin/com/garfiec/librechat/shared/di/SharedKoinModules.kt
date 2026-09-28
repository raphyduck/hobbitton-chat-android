package com.garfiec.librechat.shared.di

import com.garfiec.librechat.core.common.di.commonModule
import com.garfiec.librechat.core.data.di.dataModule
import com.garfiec.librechat.core.logging.di.loggingModule
import com.garfiec.librechat.core.network.di.networkModule
import com.garfiec.librechat.feature.auth.di.authModule
import org.koin.core.module.Module

/**
 * The Koin modules of the shared graph, in the order `LibreChatApplication` starts them. The
 * engine's own graph (`engineModule`, `tasksModule`) is added next to this list by the application.
 */
val sharedKoinModules: List<Module> = listOf(
    commonModule,
    loggingModule,
    networkModule,
    dataModule,
    authModule,
    sharedAppModule,
)
