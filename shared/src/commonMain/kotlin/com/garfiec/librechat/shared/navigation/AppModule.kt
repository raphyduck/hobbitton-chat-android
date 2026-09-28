package com.garfiec.librechat.shared.navigation

import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.portal.PortalSignOut
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import com.garfiec.librechat.shared.engine.EngineInstructionsViewModel
import com.garfiec.librechat.shared.engine.EngineShellViewModel
import kotlinx.serialization.modules.SerializersModule
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val sharedAppModule = module {
    viewModelOf(::NavHostViewModel)
    single<SerializersModule>(named("navigation")) { navigationSerializersModule }

    // The engine shell (D-077). Lambda form for `getOrNull`: the engine's graph — its addresses,
    // the portal's tokens and sign-out, the repository — is Android-only (D-034), and this module
    // is started on both platforms. Without it the shell reads as signed out.
    @Suppress("DeprecatedKoinApi")
    viewModel {
        EngineShellViewModel(
            settings = getOrNull<EngineSettingsStore>(),
            tokens = getOrNull<EngineTokenStore>(),
            portalSignOut = getOrNull<PortalSignOut>(),
            repository = getOrNull<EngineMissionRepository>(),
            kinds = get(),
            positions = get(),
            themeDataStore = get(),
            settingsDataStore = get(),
            prefetchScheduler = get(),
        )
    }

    // The global instructions' editor (D-077). Local storage only — `GlobalProfileEditor` is bound
    // by :core:data on both platforms — so a plain constructor definition.
    viewModelOf(::EngineInstructionsViewModel)
}
