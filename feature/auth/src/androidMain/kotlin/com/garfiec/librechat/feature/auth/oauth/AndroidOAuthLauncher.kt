package com.garfiec.librechat.feature.auth.oauth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent

class AndroidOAuthLauncher(
    private val context: Context,
) : OAuthLauncher {

    /**
     * The `openid` sign-in runs in the login screen's own web view (`PortalLoginViewModel`), whose
     * jar is the `CookieManager` read below — so the refresh cookie the server sets is readable.
     */
    override val embedsPortal: Boolean = true

    /**
     * The other providers still go through a Custom Tab. Known limitation, inherited: a Custom Tab
     * has the browser's cookie jar, not the app's, so [extractTokenFromCookies] cannot see what
     * the server sets there. Only `openid` is configured on the servers this fork targets.
     */
    override fun launchOAuth(provider: String, serverUrl: String) {
        val oauthUrl = oauthEntryUrl(serverUrl, provider)
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        // Launched from the application context (not an Activity), so the Custom Tab
        // intent needs NEW_TASK or startActivity() throws AndroidRuntimeException.
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            customTabsIntent.launchUrl(context, Uri.parse(oauthUrl))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No browser available to sign in", Toast.LENGTH_LONG).show()
        }
    }

    override fun extractTokenFromCookies(serverUrl: String): String? {
        val cookies = CookieManager.getInstance().getCookie(serverUrl) ?: return null
        return cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("refreshToken=") }
            ?.substringAfter("refreshToken=")
            ?.takeIf { it.isNotEmpty() }
    }

    override fun clearOAuthCookie(serverUrl: String) {
        CookieManager.getInstance()
            .setCookie(serverUrl, "refreshToken=; Path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT")
    }
}
