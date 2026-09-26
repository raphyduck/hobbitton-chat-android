package com.garfiec.librechat.feature.settings.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.garfiec.librechat.core.ui.util.isSafeExternalUri
import org.koin.mp.KoinPlatformTools

/**
 * The schemes this launcher will hand to another app: `otpauth` (its documented purpose) beside
 * the web/mail set. The URL comes from the server's 2FA enrollment answer, so it is not trusted
 * to pick an arbitrary scheme — an `intent:` or app-specific URI would otherwise fire an
 * ACTION_VIEW into whatever handles it (review C5, 26/09/2026).
 */
private val LAUNCHABLE_SCHEMES = setOf("otpauth")

actual fun openUri(uri: String): Boolean {
    if (!isSafeExternalUri(uri, extraSchemes = LAUNCHABLE_SCHEMES)) return false
    val context = KoinPlatformTools.defaultContext().get().get<Context>()
    // Android 11+ package visibility hides other apps from resolveActivity(), but startActivity()
    // is still resolved by the system, so catching ActivityNotFoundException -- not pre-checking --
    // is the way to detect "no authenticator installed" without a <queries> manifest entry.
    return try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
