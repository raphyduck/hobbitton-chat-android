package com.librechat.android.feature.auth.oauth

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import androidx.browser.customtabs.CustomTabsIntent
import com.librechat.android.core.data.datastore.ServerDataStore
class OAuthManager(
    private val serverDataStore: ServerDataStore,
) {
    /**
     * Opens Chrome Custom Tab for OAuth provider.
     * URL: {serverUrl}/api/oauth/{provider}
     * The server handles the OAuth flow and redirects back with cookies set.
     */
    fun launchOAuth(context: Context, provider: String) {
        val serverUrl = serverDataStore.getBaseUrl()
        val oauthUrl = "$serverUrl/api/oauth/$provider"
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(context, Uri.parse(oauthUrl))
    }

    /**
     * After OAuth redirect, extract refresh token from cookies.
     * Called from Activity.onResume() after Custom Tab closes.
     *
     * CookieManager shares cookie jar with Chrome Custom Tabs when the
     * default browser is Chrome. httpOnly cookies are readable via native
     * CookieManager.getCookie() despite the httpOnly flag.
     */
    fun extractTokenFromCookies(serverUrl: String): String? {
        val cookies = CookieManager.getInstance().getCookie(serverUrl) ?: return null
        return cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("refreshToken=") }
            ?.substringAfter("refreshToken=")
    }

    /**
     * Clear the refresh token cookie after successful extraction to avoid
     * stale cookie reads on subsequent onResume calls.
     */
    fun clearOAuthCookie(serverUrl: String) {
        CookieManager.getInstance()
            .setCookie(serverUrl, "refreshToken=; expires=Thu, 01 Jan 1970 00:00:00 GMT")
    }
}
