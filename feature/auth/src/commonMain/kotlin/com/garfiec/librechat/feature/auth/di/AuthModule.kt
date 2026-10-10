package com.garfiec.librechat.feature.auth.di

import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.engine.PlatformDefaults
import com.garfiec.librechat.core.data.portal.PortalTasksSignIn
import com.garfiec.librechat.feature.auth.viewmodel.PortalLoginViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val authModule = module {
    // hobbitton (D-076, D-077): the only sign-in, the portal. Lambda form for `getOrNull`: the
    // addresses and the round trip are bound by `engineModule`; without them the screen says the
    // sign-in is not available rather than failing. The build's default platform is `:app`'s
    // (lot 4); without it the screen asks for the addresses.
    @Suppress("DeprecatedKoinApi")
    viewModel {
        PortalLoginViewModel(
            settings = getOrNull<EngineSettingsStore>(),
            tasks = getOrNull<PortalTasksSignIn>(),
            defaults = getOrNull<PlatformDefaults>(),
        )
    }
}
