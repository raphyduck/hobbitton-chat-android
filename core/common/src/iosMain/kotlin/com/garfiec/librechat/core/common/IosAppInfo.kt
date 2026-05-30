package com.garfiec.librechat.core.common

import platform.Foundation.NSBundle

internal class IosAppInfo : AppInfo {
    private val bundle = NSBundle.mainBundle

    override val versionName: String =
        bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "unknown"

    override val versionCode: Long =
        (bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toLongOrNull() ?: 0L

    // Reads an Info.plist "GitSHA" key if the iOS build injects one; until that's wired into the
    // Xcode/xcconfig build, this resolves to "unknown". Android bakes the real value via BuildConfig.
    override val gitSha: String =
        bundle.objectForInfoDictionaryKey("GitSHA") as? String ?: "unknown"
}
