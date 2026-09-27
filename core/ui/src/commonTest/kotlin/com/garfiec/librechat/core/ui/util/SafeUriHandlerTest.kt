package com.garfiec.librechat.core.ui.util

import androidx.compose.ui.platform.UriHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The gate every link in the app goes through (review C5): only a web or mail link reaches the
 * platform, whatever text it was shown under.
 */
class SafeUriHandlerTest {

    @Test
    fun `web links are accepted whatever the case of the scheme`() {
        assertTrue(isSafeExternalUri("https://example.org/path?q=1"))
        assertTrue(isSafeExternalUri("HTTP://example.org"))
        assertTrue(isSafeExternalUri("  https://example.org  "))
    }

    @Test
    fun `a mail link is accepted`() {
        assertTrue(isSafeExternalUri("mailto:someone@example.org"))
    }

    @Test
    fun `schemes that reach other apps or the page itself are refused`() {
        listOf("javascript", "intent", "file", "content", "tel", "sms", "market", "librechat", "data")
            .forEach { scheme ->
                assertFalse(isSafeExternalUri("$scheme:anything"), "scheme $scheme must be refused")
                assertFalse(isSafeExternalUri("$scheme://host/path"), "scheme $scheme must be refused")
            }
    }

    @Test
    fun `a web link without an authority is refused`() {
        assertFalse(isSafeExternalUri("https:"))
        assertFalse(isSafeExternalUri("https://"))
        assertFalse(isSafeExternalUri("https:///path"))
        assertFalse(isSafeExternalUri("http:example.org"))
    }

    @Test
    fun `a scheme-less or empty link is refused`() {
        assertFalse(isSafeExternalUri(""))
        assertFalse(isSafeExternalUri("example.org"))
        assertFalse(isSafeExternalUri("//example.org"))
        assertFalse(isSafeExternalUri(":https://example.org"))
    }

    @Test
    fun `a link carrying control characters is refused`() {
        assertFalse(isSafeExternalUri("https://example.org/\u0000"))
        assertFalse(isSafeExternalUri("https://exam\nple.org"))
    }

    @Test
    fun `an extra scheme is accepted only when the caller allows it`() {
        assertFalse(isSafeExternalUri("otpauth://totp/x"))
        assertTrue(isSafeExternalUri("otpauth://totp/x", extraSchemes = setOf("otpauth")))
    }

    @Test
    fun `the handler forwards only what the gate accepts`() {
        val opened = mutableListOf<String>()
        val handler = SafeUriHandler(
            object : UriHandler {
                override fun openUri(uri: String) {
                    opened += uri
                }
            },
        )

        handler.openUri("https://example.org")
        handler.openUri("tel:123")
        handler.openUri("mailto:a@example.org")

        assertEquals(listOf("https://example.org", "mailto:a@example.org"), opened)
    }

    @Test
    fun `a platform failure on an accepted link does not propagate`() {
        val handler = SafeUriHandler(
            object : UriHandler {
                override fun openUri(uri: String) = throw IllegalArgumentException("no browser")
            },
        )

        handler.openUri("https://example.org")
    }
}
