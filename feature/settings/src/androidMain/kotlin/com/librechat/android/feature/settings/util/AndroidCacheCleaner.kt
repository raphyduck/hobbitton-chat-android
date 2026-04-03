package com.librechat.android.feature.settings.util

import android.content.Context

class AndroidCacheCleaner(private val context: Context) : PlatformCacheCleaner {
    override fun clearCache() {
        context.cacheDir.deleteRecursively()
    }
}
