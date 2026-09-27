package com.garfiec.librechat.core.ui.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import co.touchlab.kermit.Logger

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PortalWebView(
    url: String?,
    onNavigation: (url: String) -> Boolean,
    modifier: Modifier,
) {
    val currentOnNavigation by rememberUpdatedState(onNavigation)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // The portal is a single-page application: no script, no login.
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Nothing a login page needs, everything a hostile one would like.
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setGeolocationEnabled(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                // The first-party jar is the product (the portal's session, the chat's refresh
                // cookie); third-party cookies are nobody's business here.
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webViewClient = PortalWebViewClient { target -> currentOnNavigation(target) }
            }
        },
        update = { view ->
            if (url != null && view.tag != url) {
                view.tag = url
                view.loadUrl(url)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.destroy()
        },
    )
}

/**
 * Offers every main-frame navigation to the caller first.
 *
 * [shouldOverrideUrlLoading] is where redirects are seen (API 24+), which is where the chat's
 * return from the portal happens: `/oauth/openid/callback` answers 302 to the web client, and that
 * hop is the one to stop on. [onPageStarted] is the fallback for a navigation that reached the
 * page without passing through it; the caller's handling is idempotent, so being offered the same
 * URL twice is harmless.
 */
private class PortalWebViewClient(
    private val onNavigation: (String) -> Boolean,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val navigation = request ?: return true
        val target = navigation.url?.toString() ?: return true
        if (navigation.isForMainFrame && onNavigation(target)) return true
        // Never let the view try a scheme it would fail on — or, worse, hand to another app.
        return !isWebNavigation(target)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (url != null && isWebNavigation(url) && onNavigation(url)) {
            view?.stopLoading()
            return
        }
        super.onPageStarted(view, url, favicon)
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.cancel()
        Logger.w { "Portal web view: TLS error ${error?.primaryError}, load cancelled" }
    }
}
