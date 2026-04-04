package com.garfiec.librechat.navigation

import kotlinx.serialization.modules.SerializersModule
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::NavHostViewModel)
    single<SerializersModule>(named("navigation")) { navigationSerializersModule }
}
