package com.garfiec.librechat.shared.di

import com.garfiec.librechat.core.data.engine.ConversationRequests
import com.garfiec.librechat.core.data.engine.EngineAttentionWatcher
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.portal.PortalSignOut
import com.garfiec.librechat.core.data.scheduler.SchedulerRepository
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import com.garfiec.librechat.shared.engine.EngineInstructionsViewModel
import com.garfiec.librechat.shared.engine.EngineShellViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val sharedAppModule = module {
    // The engine shell (D-077). Lambda form for `getOrNull`: the engine's graph — its addresses,
    // the portal's tokens and sign-out, the repository — is bound by `engineModule` and
    // `tasksModule`, and the shell reads as signed out without it.
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
            attentionWatcher = getOrNull<EngineAttentionWatcher>(),
            conversationRequests = getOrNull<ConversationRequests>(),
            scheduler = getOrNull<SchedulerRepository>(),
        )
    }

    // The global instructions' editor (D-077). Local storage only — `GlobalProfileEditor` is bound
    // by :core:data — so a plain constructor definition.
    viewModelOf(::EngineInstructionsViewModel)
}
