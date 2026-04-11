package com.garfiec.librechat.feature.auth.oauth

import co.touchlab.kermit.Logger
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject

class IosOAuthLauncher : OAuthLauncher {

    // Retain the session to prevent deallocation during auth flow
    private var authSession: ASWebAuthenticationSession? = null

    override fun launchOAuth(provider: String, serverUrl: String) {
        val oauthUrl = "$serverUrl/api/oauth/$provider"
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

        val window = getKeyWindow()
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

    private fun getKeyWindow(): UIWindow? {
        val scene = UIApplication.sharedApplication.connectedScenes
            .firstOrNull() as? UIWindowScene
        return scene?.windows?.firstOrNull {
            (it as? UIWindow)?.isKeyWindow() == true
        } as? UIWindow
    }
}
