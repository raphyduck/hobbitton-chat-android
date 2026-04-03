package com.garfiec.librechat.navigation

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::NavHostViewModel)
}
