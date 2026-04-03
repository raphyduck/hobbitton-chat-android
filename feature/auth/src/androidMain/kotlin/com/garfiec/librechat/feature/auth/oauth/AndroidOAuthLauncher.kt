package com.garfiec.librechat.feature.auth.oauth

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import androidx.browser.customtabs.CustomTabsIntent

class AndroidOAuthLauncher(
    private val context: Context,
) : OAuthLauncher {

    override fun launchOAuth(provider: String, serverUrl: String) {
        val oauthUrl = "$serverUrl/api/oauth/$provider"
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(context, Uri.parse(oauthUrl))
    }

    override fun extractTokenFromCookies(serverUrl: String): String? {
        val cookies = CookieManager.getInstance().getCookie(serverUrl) ?: return null
        return cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("refreshToken=") }
            ?.substringAfter("refreshToken=")
    }

    override fun clearOAuthCookie(serverUrl: String) {
        CookieManager.getInstance()
            .setCookie(serverUrl, "refreshToken=; expires=Thu, 01 Jan 1970 00:00:00 GMT")
    }
}
