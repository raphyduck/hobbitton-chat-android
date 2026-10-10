package com.garfiec.librechat.core.data.engine

/**
 * The platform a build was made for (lot 4, 10/10/2026): the three addresses the sign-in starts
 * from, so a person installing the app signs in without typing anything.
 *
 * They come from a `platform.properties` at the repository root, which is not versioned (the
 * repository stays generic), through the application plugin's `BuildConfig` fields; `:app` binds
 * what it was built with. A build made without the file carries empty addresses and the sign-in
 * asks for them as before. Bound by `:app`, resolved with `getOrNull`: the sign-in works without it.
 */
data class PlatformDefaults(
    val baseUrl: String = "",
    val schedulerUrl: String = "",
    val issuerUrl: String = "",
) {
    /** All three addresses are named: the sign-in can start on them. */
    val complete: Boolean
        get() = baseUrl.isNotBlank() && schedulerUrl.isNotBlank() && issuerUrl.isNotBlank()

    companion object {
        val NONE = PlatformDefaults()
    }
}
