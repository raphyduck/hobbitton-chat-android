package com.garfiec.librechat.shared.di

import com.garfiec.librechat.core.data.engine.ConversationModelStore
import com.garfiec.librechat.core.data.engine.ConversationRequests
import com.garfiec.librechat.core.data.engine.DeviceSession
import com.garfiec.librechat.core.data.engine.EngineAttentionWatcher
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.engine.TranscriptCache
import com.garfiec.librechat.core.data.lock.AppLock
import com.garfiec.librechat.core.data.portal.PortalSignOut
import com.garfiec.librechat.core.data.scheduler.ChatBudgetSource
import com.garfiec.librechat.core.data.scheduler.SchedulerRepository
import com.garfiec.librechat.core.logging.DiagnosticLogShare
import com.garfiec.librechat.core.network.engine.EngineTokenStore
import com.garfiec.librechat.shared.engine.EngineBudgetViewModel
import com.garfiec.librechat.shared.engine.EngineInstructionsViewModel
import com.garfiec.librechat.shared.engine.EngineShellViewModel
import com.garfiec.librechat.shared.engine.lock.AppLockViewModel
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
            reads = get(),
            themeDataStore = get(),
            settingsDataStore = get(),
            attentionWatcher = getOrNull<EngineAttentionWatcher>(),
            conversationRequests = getOrNull<ConversationRequests>(),
            scheduler = getOrNull<SchedulerRepository>(),
            diagnostics = getOrNull<DiagnosticLogShare>(),
            transcripts = getOrNull<TranscriptCache>(),
            conversationModels = getOrNull<ConversationModelStore>(),
            deviceSession = getOrNull<DeviceSession>(),
            appLock = getOrNull<AppLock>(),
        )
    }

    // The app's lock (28/10/2026): the gate over the signed-in app and the Settings section.
    @Suppress("DeprecatedKoinApi")
    viewModel { AppLockViewModel(lock = getOrNull<AppLock>()) }

    // The global instructions' editor (D-077). Local storage only — `GlobalProfileEditor` is bound
    // by :core:data — so a plain constructor definition.
    viewModelOf(::EngineInstructionsViewModel)

    // The conversations' spending ceilings (12/10/2026). `getOrNull` for the same reason as the
    // shell: the scheduler is the engine graph's, and without it the screen says so.
    @Suppress("DeprecatedKoinApi")
    viewModel { EngineBudgetViewModel(source = getOrNull<ChatBudgetSource>()) }
}
