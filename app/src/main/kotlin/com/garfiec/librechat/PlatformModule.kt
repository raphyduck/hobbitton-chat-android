package com.garfiec.librechat

import com.garfiec.librechat.core.data.engine.DeviceCleanup
import com.garfiec.librechat.core.data.engine.PlatformDefaults
import com.garfiec.librechat.core.logging.DiagnosticLogShare
import com.garfiec.librechat.diagnostics.AndroidDiagnosticLogShare
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The platform this build was made for (lot 4, 10/10/2026): the three addresses `platform.properties`
 * named at build time, as the sign-in's defaults. Empty when the build had no such file; the
 * sign-in then asks for them. Also the phone's share sheet for the diagnostic log (20/10/2026):
 * Settings → Plateforme, the one way to read the app's own traces off a phone.
 */
val platformModule = module {
    single<DiagnosticLogShare> { AndroidDiagnosticLogShare(androidContext(), get()) }
    single<DeviceCleanup> { AndroidDeviceCleanup(androidContext(), get()) }
    single {
        PlatformDefaults(
            baseUrl = BuildConfig.PLATFORM_ENGINE_URL,
            schedulerUrl = BuildConfig.PLATFORM_SCHEDULER_URL,
            issuerUrl = BuildConfig.PLATFORM_PORTAL_URL,
        )
    }
}
