package com.garfiec.librechat.core.data.repository

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android switch-cache clear: WebView storage (localStorage/IndexedDB behind inline artifacts),
 * the process-global WebView cookie jar, and the WebView HTTP cache. The jar holds the OAuth
 * `refreshToken` cookie for every server the user has OAuth'd against — per-URL clearing would
 * leave the outgoing account's cookie live (it would be read against the *current* URL), so clear
 * it whole; an interactive OAuth re-issues on demand. The HTTP cache is process-global too and
 * kept the images and scripts the outgoing account's artifacts fetched (review C9, 26/09/2026);
 * `clearCache` is an instance method, so a throwaway WebView is created for the call. WebView
 * instances themselves die with their composables when the switch pops the back stack.
 * Best-effort: a clear failure must never abort the switch itself.
 */
class AndroidSwitchCacheCleaner(context: Context) : SwitchCacheCleaner {

    private val appContext = context.applicationContext

    override suspend fun clearOnSwitch() {
        runCatching {
            withContext(Dispatchers.Main) {
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }
                WebView(appContext).apply {
                    clearCache(true)
                    destroy()
                }
            }
        }.onFailure { Logger.w(it) { "Switch cache clear failed" } }
    }
}
