package com.librechat.android.shared.navigation

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val sharedAppModule = module {
    viewModelOf(::NavHostViewModel)
}
