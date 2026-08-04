package com.garfiec.librechat.core.common

import platform.Foundation.NSBundle

internal class IosAppInfo : AppInfo {
    private val bundle = NSBundle.mainBundle

    // "LibreChatVersionName" is stamped by the "Stamp Version" Xcode build phase with the
    // full versionName (incl. any -rcN suffix); CFBundleShortVersionString carries only the
    // numeric calver core because App Store Connect rejects non-numeric versions there.
    override val versionName: String =
        bundle.objectForInfoDictionaryKey("LibreChatVersionName") as? String
            ?: bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
            ?: "unknown"

    // CFBundleVersion is YYYYMMPP, or "YYYYMMPP.N" on App Store uploads (the .N suffix keeps
    // each upload unique for App Store Connect) — the integer prefix is the versionCode.
    override val versionCode: Long =
        (bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
            ?.substringBefore('.')
            ?.toLongOrNull() ?: 0L

    // Reads the Info.plist "GitSHA" key stamped by the "Stamp Git SHA" Xcode build phase
    // (git rev-parse --short=8). Falls back to "unknown" if absent. Android bakes it via BuildConfig.
    override val gitSha: String =
        bundle.objectForInfoDictionaryKey("GitSHA") as? String ?: "unknown"
}
