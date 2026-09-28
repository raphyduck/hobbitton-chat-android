package com.garfiec.librechat.feature.auth.oauth

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.ui.platform.currentKeyWindow
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSURL
import platform.darwin.NSObject

class IosOAuthLauncher : OAuthLauncher {

    // Retain the session to prevent deallocation during auth flow
    private var authSession: ASWebAuthenticationSession? = null

    override fun launchOAuth(provider: String, serverUrl: String) {
        // `/oauth/…`, not `/api/oauth/…` (see oauthEntryUrl). The cookie read below still cannot
        // see what the authentication session's own jar received — a known, inherited limitation;
        // the single sign-in (D-076) is Android-only, like the engine graph (D-034).
        val oauthUrl = oauthEntryUrl(serverUrl, provider)
        val url = NSURL.URLWithString(oauthUrl) ?: run {
            Logger.e { "Invalid OAuth URL: $oauthUrl" }
            return
        }

        val session = ASWebAuthenticationSession(
            uRL = url,
            callbackURLScheme = "librechat",
            completionHandler = { _, error ->
                if (error != null) {
                    Logger.d { "ASWebAuthenticationSession ended: ${error.localizedDescription}" }
                }
                authSession = null
            },
        )

        session.prefersEphemeralWebBrowserSession = false

        val window = currentKeyWindow()
        if (window != null) {
            val contextProvider = object : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
                override fun presentationAnchorForWebAuthenticationSession(
                    session: ASWebAuthenticationSession,
                ): ASPresentationAnchor {
                    return window
                }
            }
            session.presentationContextProvider = contextProvider
        }

        authSession = session
        session.start()
    }

    override fun extractTokenFromCookies(serverUrl: String): String? {
        val url = NSURL.URLWithString(serverUrl) ?: return null
        val cookies = NSHTTPCookieStorage.sharedHTTPCookieStorage.cookiesForURL(url) ?: return null

        @Suppress("UNCHECKED_CAST")
        val cookieList = cookies as List<NSHTTPCookie>
        return cookieList.firstOrNull { it.name == "refreshToken" }?.value
    }

    override fun clearOAuthCookie(serverUrl: String) {
        val url = NSURL.URLWithString(serverUrl) ?: return
        val cookies = NSHTTPCookieStorage.sharedHTTPCookieStorage.cookiesForURL(url) ?: return

        @Suppress("UNCHECKED_CAST")
        val cookieList = cookies as List<NSHTTPCookie>
        cookieList.filter { it.name == "refreshToken" }.forEach {
            NSHTTPCookieStorage.sharedHTTPCookieStorage.deleteCookie(it)
        }
    }
}
