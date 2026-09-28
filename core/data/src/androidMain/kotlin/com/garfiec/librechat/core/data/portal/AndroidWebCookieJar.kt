package com.garfiec.librechat.core.data.portal

import android.webkit.CookieManager
import co.touchlab.kermit.Logger
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The process-wide `CookieManager` — the jar the portal login's web view writes into — scoped to
 * the origins a sign-out names.
 *
 * `CookieManager` has no « delete this host's cookies » call: the only way to remove one is to
 * write it again, expired, with the same name, domain and path. [expiringCookies] computes those
 * writes; this class only reads the names back and applies them. On the main thread, as the
 * account-switch cleaner does with the same object. Best effort: a failure is logged, never thrown
 * into the logout it runs in.
 */
class AndroidWebCookieJar : WebCookieJar {

    override suspend fun expire(origins: List<String>) {
        if (origins.isEmpty()) return
        runCatching {
            withContext(Dispatchers.Main) {
                val manager = CookieManager.getInstance()
                origins.forEach { origin ->
                    val host = runCatching { Url(origin).host }.getOrNull() ?: return@forEach
                    val secure = origin.startsWith("https://", ignoreCase = true)
                    expiringCookies(manager.getCookie(origin), host, secure).forEach { cookie ->
                        manager.setCookie(origin, cookie)
                    }
                }
                manager.flush()
            }
        }.onFailure { Logger.w(it) { "Could not expire the portal's web cookies" } }
    }
}
