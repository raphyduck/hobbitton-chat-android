package com.garfiec.librechat

import com.garfiec.librechat.core.data.engine.PlatformDefaults
import org.koin.dsl.module

/**
 * The platform this build was made for (lot 4, 10/10/2026): the three addresses `platform.properties`
 * named at build time, as the sign-in's defaults. Empty when the build had no such file; the
 * sign-in then asks for them.
 */
val platformModule = module {
    single {
        PlatformDefaults(
            baseUrl = BuildConfig.PLATFORM_ENGINE_URL,
            schedulerUrl = BuildConfig.PLATFORM_SCHEDULER_URL,
            issuerUrl = BuildConfig.PLATFORM_PORTAL_URL,
        )
    }
}
