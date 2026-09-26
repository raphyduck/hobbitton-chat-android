package com.garfiec.librechat.feature.chat.components.artifact

/**
 * What a WebView showing one template may fetch — enforced natively, in `shouldInterceptRequest`,
 * where the document cannot argue.
 *
 * A `<meta>` Content-Security-Policy only binds content parsed after it and only when it sits
 * first in `<head>`; in a document the artifact itself wrote that is not guaranteed (review C2).
 * This policy is the layer the content cannot reach: a main-frame request is never allowed (the
 * only document a template WebView shows is the one the app loaded, so any navigation — a link, a
 * form, a redirect — is refused), and a subresource loads only over `https` from the CDN hosts the
 * template names, or from any `https` host when the template renders artifact-authored images
 * ([anyHttps]). Everything else — `http:`, `file:`, `content:`, `intent:`, `ws:` — is blocked
 * (review C1, 26/09/2026).
 */
class WebResourcePolicy(
    private val hosts: Set<String>,
    private val anyHttps: Boolean,
) {

    fun allows(url: String, isMainFrame: Boolean): Boolean =
        if (isMainFrame) allowsMainFrame(url) else allowsSubresource(url)

    /** The app's own document only; no template ever navigates. */
    private fun allowsMainFrame(url: String): Boolean =
        url == ABOUT_BLANK || url == ABOUT_SRCDOC || url.startsWith(DATA_SCHEME, ignoreCase = true)

    private fun allowsSubresource(url: String): Boolean {
        if (url.startsWith(DATA_SCHEME, ignoreCase = true) || url.startsWith(BLOB_SCHEME, ignoreCase = true)) {
            return true
        }
        val host = httpsHostOf(url) ?: return false
        return anyHttps || host in hosts
    }

    companion object {
        private const val ABOUT_BLANK = "about:blank"
        private const val ABOUT_SRCDOC = "about:srcdoc"
        private const val DATA_SCHEME = "data:"
        private const val BLOB_SCHEME = "blob:"
        private const val HTTPS_PREFIX = "https://"

        /** Loads nothing from the network at all (escaped code, SVG). */
        val NONE = WebResourcePolicy(emptySet(), anyHttps = false)

        /**
         * The lower-cased host of an `https` URL, or null when [url] is not `https`, carries user
         * info (an `@` lets `https://cdn.jsdelivr.net@evil.example/` read as the CDN to a naive
         * check) or names a non-default port.
         */
        internal fun httpsHostOf(url: String): String? {
            if (!url.startsWith(HTTPS_PREFIX, ignoreCase = true)) return null
            val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), HTTPS_PREFIX.length)
                .let { if (it < 0) url.length else it }
            val authority = url.substring(HTTPS_PREFIX.length, authorityEnd)
            if (authority.isEmpty() || '@' in authority) return null
            val host = authority.removeSuffix(":443")
            if (host.isEmpty() || (':' in host && !host.startsWith('['))) return null
            return host.lowercase()
        }
    }
}
