package com.garfiec.librechat.feature.chat.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which image URLs count as the server's own (review C7): scheme, host and port, not host alone. */
class UrlOriginTest {

    private val server = "https://chat.example.org"

    @Test
    fun `a path under the server is the server`() {
        assertTrue(isSameOrigin("https://chat.example.org/api/files/1.png", server))
        assertTrue(isSameOrigin("HTTPS://CHAT.example.org:443/images/x.png", server))
    }

    @Test
    fun `the same host on another scheme or port is not the server`() {
        assertFalse(isSameOrigin("http://chat.example.org/api/files/1.png", server))
        assertFalse(isSameOrigin("https://chat.example.org:8443/api/files/1.png", server))
        assertFalse(isSameOrigin("https://chat.example.org/x", "http://chat.example.org"))
    }

    @Test
    fun `another host is not the server`() {
        assertFalse(isSameOrigin("https://example.org/chat.example.org/x.png", server))
        assertFalse(isSameOrigin("https://chat.example.org.evil.test/x.png", server))
        assertFalse(isSameOrigin("https://user@chat.example.org/x.png", "https://user@other.test"))
    }

    @Test
    fun `non-web urls are never the server`() {
        assertFalse(isSameOrigin("data:image/png;base64,AAAA", server))
        assertFalse(isSameOrigin("file:///sdcard/x.png", server))
        assertFalse(isSameOrigin("", server))
        assertFalse(isSameOrigin("https://chat.example.org/x", ""))
    }

    @Test
    fun `the server's own base url may carry a port or a path`() {
        assertTrue(isSameOrigin("https://chat.example.org:8443/x", "https://chat.example.org:8443/librechat"))
        assertTrue(isSameOrigin("http://10.0.0.5:3080/api/files/1", "http://10.0.0.5:3080"))
    }

    @Test
    fun `origins are normalised`() {
        assertEquals("https://chat.example.org:443", originOf("https://Chat.Example.org/a?b#c"))
        assertEquals("http://h:80", originOf("http://h"))
        assertEquals("https://[::1]:8443", originOf("https://[::1]:8443/x"))
        assertEquals(null, originOf("https://h:notaport/x"))
        assertEquals(null, originOf("ftp://h/x"))
    }
}
