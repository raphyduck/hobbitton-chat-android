package com.garfiec.librechat.feature.chat.components.web

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.ui.util.isSafeExternalUri
import com.garfiec.librechat.feature.chat.components.artifact.WebResourcePolicy
import java.io.ByteArrayInputStream

/**
 * The one configuration every content WebView in this module shares (artifact previews, the
 * chat's mermaid card, KaTeX). Review C1 (26/09/2026): these views used to run with DOM storage
 * on and no navigation control, under the real origin `https://cdn.jsdelivr.net`.
 *
 * DOM storage stays off: the documents are loaded with an opaque origin (see
 * [loadIsolatedDocument]) where `localStorage` throws anyway, and none of the templates need it.
 * File and content access were already off; geolocation and window opening are closed for the
 * same reason — nothing here asks for them.
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.applyLockedDownSettings() {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.setGeolocationEnabled(false)
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportMultipleWindows(false)
}

/**
 * Loads [html] with a null base URL: the document gets `about:blank` as its base and therefore
 * an opaque origin — no cookie jar (the process-global one carries the OAuth `refreshToken`
 * cookie), no storage shared across artifacts, and nothing for a form to post to. A pinned CDN
 * `<script src>` still loads, since a classic script needs no CORS and the CDNs used answer
 * `Access-Control-Allow-Origin: *` for the ones that do (SRI).
 */
internal fun WebView.loadIsolatedDocument(html: String) {
    loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

/**
 * A [WebViewClient] that refuses every navigation and every fetch a [WebResourcePolicy] does not
 * name. Sites subclass it for their own page callbacks.
 *
 * - [shouldOverrideUrlLoading] always answers `true`: the WebView never leaves the document the
 *   app loaded. A link the user actually tapped ([WebResourceRequest.hasGesture]) with an
 *   `http`/`https` target is handed to [openExternally] — the root `SafeUriHandler` — so a link
 *   in a Markdown artifact still opens, in the browser, where the address is visible. A
 *   navigation the page started on its own is dropped.
 * - [shouldInterceptRequest] is the layer the content cannot precede or override: it sees every
 *   request, including a form POST (which the override callback does not), and answers a blocked
 *   response for anything outside the policy.
 *
 * [policy] is read on each request rather than captured, because a preview surface can be
 * handed an artifact of another type without being recreated.
 */
internal open class LockedDownWebViewClient(
    private val policy: () -> WebResourcePolicy,
    private val openExternally: (String) -> Unit,
    private val surface: String,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString().orEmpty()
        if (request?.hasGesture() == true && isSafeExternalUri(url)) {
            openExternally(url)
        } else {
            Logger.d { "$surface WebView: navigation refused" }
        }
        return true
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString() ?: return blockedResponse()
        if (policy().allows(url, request.isForMainFrame)) return null
        Logger.d { "$surface WebView: blocked a request (main frame: ${request.isForMainFrame})" }
        return blockedResponse()
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.cancel()
        Logger.w { "SSL error in $surface WebView: ${error?.primaryError}" }
    }

    private fun blockedResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "UTF-8", HTTP_FORBIDDEN, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private companion object {
        const val HTTP_FORBIDDEN = 403
    }
}
