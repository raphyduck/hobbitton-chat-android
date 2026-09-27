package com.garfiec.librechat.core.network.client

import io.ktor.http.URLBuilder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The one authority rule every credential goes through since 26/09/2026 (finding M3): scheme,
 * host and effective port, fail-closed.
 */
class HostScopingTest {

    private fun url(value: String) = URLBuilder(value)

    @Test
    fun `the server's own authority matches, case-insensitively on the host`() {
        assertTrue(isSameServerAuthority(url("https://Chat.Example.com/api/x"), "https://chat.example.com"))
    }

    @Test
    fun `an explicit default port is the same authority as an implicit one`() {
        assertTrue(isSameServerAuthority(url("https://chat.example.com:443/api"), "https://chat.example.com"))
        assertTrue(isSameServerAuthority(url("https://chat.example.com/api"), "https://chat.example.com:443"))
    }

    @Test
    fun `a scheme downgrade on the same host is another authority`() {
        assertFalse(isSameServerAuthority(url("http://chat.example.com/api"), "https://chat.example.com"))
    }

    @Test
    fun `another port on the same host is another authority`() {
        assertFalse(isSameServerAuthority(url("https://chat.example.com:8443/api"), "https://chat.example.com"))
        assertFalse(isSameServerAuthority(url("https://chat.example.com/api"), "https://chat.example.com:8080"))
    }

    @Test
    fun `a subdomain is another authority`() {
        assertFalse(isSameServerAuthority(url("https://cdn.chat.example.com/x"), "https://chat.example.com"))
    }

    @Test
    fun `an unknown base URL matches nothing`() {
        assertFalse(isSameServerAuthority(url("https://chat.example.com/api"), null))
        assertFalse(isSameServerAuthority(url("https://chat.example.com/api"), ""))
        assertFalse(isSameServerAuthority(url("https://chat.example.com/api"), "not a url"))
    }
}
