package com.garfiec.librechat.feature.chat.components.artifact

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The natively enforced fetch policy under every template WebView (review C1/C2). */
class WebResourcePolicyTest {

    private val cdnOnly = WebResourcePolicy(setOf("cdn.jsdelivr.net"), anyHttps = false)
    private val cdnAndImages = WebResourcePolicy(setOf("cdn.jsdelivr.net"), anyHttps = true)

    @Test
    fun `no template ever navigates`() {
        listOf("https://cdn.jsdelivr.net/x.js", "https://example.org/", "http://example.org/", "file:///etc/hosts")
            .forEach { assertFalse(cdnAndImages.allows(it, isMainFrame = true), "main frame to $it") }
    }

    @Test
    fun `the app's own document is the one main frame allowed`() {
        assertTrue(cdnOnly.allows("about:blank", isMainFrame = true))
        assertTrue(cdnOnly.allows("about:srcdoc", isMainFrame = true))
        assertTrue(cdnOnly.allows("data:text/html,x", isMainFrame = true))
    }

    @Test
    fun `a named CDN host loads over https only`() {
        assertTrue(cdnOnly.allows("https://cdn.jsdelivr.net/npm/mermaid@10.9.8/dist/mermaid.min.js", isMainFrame = false))
        assertTrue(cdnOnly.allows("HTTPS://CDN.JSDELIVR.NET/x.js", isMainFrame = false))
        assertTrue(cdnOnly.allows("https://cdn.jsdelivr.net:443/x.js", isMainFrame = false))
        assertFalse(cdnOnly.allows("http://cdn.jsdelivr.net/x.js", isMainFrame = false))
    }

    @Test
    fun `a host that is not named is refused`() {
        assertFalse(cdnOnly.allows("https://example.org/pixel.png", isMainFrame = false))
        assertFalse(cdnOnly.allows("https://cdn.jsdelivr.net.example.org/x.js", isMainFrame = false))
    }

    @Test
    fun `user info and a non-default port do not pass as the CDN`() {
        assertFalse(cdnOnly.allows("https://cdn.jsdelivr.net@example.org/x.js", isMainFrame = false))
        assertFalse(cdnOnly.allows("https://cdn.jsdelivr.net:8443/x.js", isMainFrame = false))
    }

    @Test
    fun `an image template accepts any https host but nothing else`() {
        assertTrue(cdnAndImages.allows("https://example.org/picture.png", isMainFrame = false))
        assertFalse(cdnAndImages.allows("http://example.org/picture.png", isMainFrame = false))
        assertFalse(cdnAndImages.allows("file:///sdcard/picture.png", isMainFrame = false))
        assertFalse(cdnAndImages.allows("content://media/external/images/1", isMainFrame = false))
        assertFalse(cdnAndImages.allows("intent://scan/#Intent;end", isMainFrame = false))
        assertFalse(cdnAndImages.allows("ws://example.org/socket", isMainFrame = false))
    }

    @Test
    fun `in-document resources are always allowed`() {
        assertTrue(WebResourcePolicy.NONE.allows("data:image/png;base64,AAAA", isMainFrame = false))
        assertTrue(WebResourcePolicy.NONE.allows("blob:null/1234", isMainFrame = false))
        assertFalse(WebResourcePolicy.NONE.allows("https://cdn.jsdelivr.net/x.js", isMainFrame = false))
    }

    @Test
    fun `host extraction lower-cases and strips the path`() {
        assertEquals("cdn.jsdelivr.net", WebResourcePolicy.httpsHostOf("https://CDN.jsdelivr.net/npm/x?y#z"))
        assertEquals("cdn.jsdelivr.net", WebResourcePolicy.httpsHostOf("https://cdn.jsdelivr.net"))
        assertEquals(null, WebResourcePolicy.httpsHostOf("https://"))
        assertEquals(null, WebResourcePolicy.httpsHostOf("http://cdn.jsdelivr.net/x"))
    }
}
