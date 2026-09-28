package com.garfiec.librechat.core.logging

import com.garfiec.librechat.core.common.AppInfo

/**
 * Emits a single structured "startup header" record at process start so every diagnostic export
 * begins with the build + device context needed to triage the records that follow. This is the
 * one place that snapshots app version, git SHA, OS, and device model into the log.
 *
 * Privacy: contains only build metadata and coarse, non-identifying device facts. NEVER add the
 * server URL, account, token, or any user data here — exports may be shared in bug reports.
 */
fun logStartupHeader(
    appInfo: AppInfo,
    platformInfo: PlatformInfo,
) {
    // Defensive: the startup path must never crash the app on a logging failure.
    runCatching {
        Diag.i(
            tag = "Startup",
            origin = LogOrigin.CLIENT,
            attrs = buildMap {
                put("versionName", appInfo.versionName)
                put("versionCode", appInfo.versionCode.toString())
                put("gitSha", appInfo.gitSha)
                put("osName", platformInfo.osName)
                put("osVersion", platformInfo.osVersion)
                put("deviceModel", platformInfo.deviceModel)
            },
        ) { "app startup" }
    }
}
