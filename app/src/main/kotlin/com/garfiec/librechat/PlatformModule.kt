package com.garfiec.librechat

import com.garfiec.librechat.core.data.engine.DeviceSession
import com.garfiec.librechat.core.data.engine.PlatformDefaults
import com.garfiec.librechat.core.logging.DiagnosticLogShare
import com.garfiec.librechat.core.network.api.SchedulerApi
import com.garfiec.librechat.diagnostics.AndroidDiagnosticLogShare
import com.garfiec.librechat.shared.engine.voice.EngineVoiceViewModel
import com.garfiec.librechat.shared.engine.voice.VoiceLink
import com.garfiec.librechat.shared.engine.voice.VoiceRooms
import com.garfiec.librechat.voice.LiveKitVoiceLink
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The platform this build was made for (lot 4, 10/10/2026): the three addresses `platform.properties`
 * named at build time, as the sign-in's defaults. Empty when the build had no such file; the
 * sign-in then asks for them. Also the phone's share sheet for the diagnostic log (20/10/2026):
 * Settings → Plateforme, the one way to read the app's own traces off a phone.
 */
val platformModule = module {
    single<DiagnosticLogShare> { AndroidDiagnosticLogShare(androidContext(), get()) }
    single<DeviceSession> { AndroidDeviceSession(androidContext(), get()) }
    // The voice mode (27/10/2026): the room comes from the scheduler, the line is LiveKit's.
    single<VoiceLink> { LiveKitVoiceLink(androidContext()) }
    single<VoiceRooms> { VoiceRooms { get<SchedulerApi>().voiceRoom() } }
    viewModel { EngineVoiceViewModel(rooms = getOrNull(), link = getOrNull()) }
    single {
        PlatformDefaults(
            baseUrl = BuildConfig.PLATFORM_ENGINE_URL,
            schedulerUrl = BuildConfig.PLATFORM_SCHEDULER_URL,
            issuerUrl = BuildConfig.PLATFORM_PORTAL_URL,
        )
    }
}
