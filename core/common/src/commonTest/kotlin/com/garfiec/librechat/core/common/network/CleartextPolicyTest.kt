package com.garfiec.librechat.core.common.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The boundary of plain `http://` (M4 and F5, 26/09/2026): a self-hosted server on the user's own
 * network is the one case it exists for, and every public host is the case it must refuse.
 */
class CleartextPolicyTest {

    @Test
    fun `https is permitted anywhere`() {
        assertTrue(CleartextPolicy.isPermitted("https://chat.example.com"))
        assertTrue(CleartextPolicy.isPermitted("https://203.0.113.10:8443/api"))
    }

    @Test
    fun `http to a public host is refused`() {
        assertFalse(CleartextPolicy.isPermitted("http://chat.example.com"))
        assertFalse(CleartextPolicy.isPermitted("http://203.0.113.10:3080"))
        assertFalse(CleartextPolicy.isPermitted("HTTP://Chat.Example.com/path"))
    }

    @Test
    fun `http to loopback and the private ranges is permitted`() {
        listOf(
            "http://localhost:3080",
            "http://127.0.0.1:3080",
            "http://10.0.2.2:3080",
            "http://192.168.1.20:3080",
            "http://172.16.0.5",
            "http://172.31.255.254",
            "http://100.64.0.1",
            "http://100.127.255.254",
            "http://169.254.10.10",
            "http://[::1]:3080",
            "http://[fd12:3456::1]/api",
            "http://[fe80::1%25en0]",
            "http://[::ffff:192.168.1.1]:3080",
        ).forEach { url -> assertTrue(CleartextPolicy.isPermitted(url), url) }
    }

    @Test
    fun `the neighbours of the private ranges are public`() {
        listOf(
            "http://172.15.0.1",
            "http://172.32.0.1",
            "http://192.169.0.1",
            "http://100.63.0.1",
            "http://100.128.0.1",
            "http://11.0.0.1",
            "http://[2001:db8::1]",
            "http://[::ffff:203.0.113.10]",
        ).forEach { url -> assertFalse(CleartextPolicy.isPermitted(url), url) }
    }

    @Test
    fun `local name suffixes and single-label names count as private`() {
        listOf("nas.local", "server.lan", "box.home.arpa", "api.internal", "printer.home", "nas", "app.localhost")
            .forEach { host -> assertTrue(CleartextPolicy.isPrivateHost(host), host) }
        listOf("chat.example.com", "local.example.com", "lan.example.org")
            .forEach { host -> assertFalse(CleartextPolicy.isPrivateHost(host), host) }
    }

    @Test
    fun `websocket cleartext follows the same rule`() {
        assertTrue(CleartextPolicy.isPermitted("ws://192.168.1.20:3080/events"))
        assertFalse(CleartextPolicy.isPermitted("ws://chat.example.com/events"))
    }

    @Test
    fun `a bare host without a scheme is not this policy's concern`() {
        assertTrue(CleartextPolicy.isPermitted("chat.example.com"))
        assertTrue(CleartextPolicy.isPermitted(""))
    }

    @Test
    fun `an http URL without a host is refused`() {
        assertFalse(CleartextPolicy.isPermitted("http://"))
        assertFalse(CleartextPolicy.isPermitted("http:///path"))
    }

    @Test
    fun `the host is read past userinfo, port, path and brackets`() {
        assertEquals("chat.example.com", CleartextPolicy.hostOf("https://user:pw@chat.example.com:8443/a?b#c"))
        assertEquals("fd12::1", CleartextPolicy.hostOf("http://[fd12::1]:3080/x"))
        assertEquals("192.168.1.20", CleartextPolicy.hostOf("http://192.168.1.20"))
        assertEquals(null, CleartextPolicy.hostOf("chat.example.com"))
    }

    @Test
    fun `an ill-formed IPv4 literal is not mistaken for a private address`() {
        assertFalse(CleartextPolicy.isPrivateHost("192.168.1"))
        assertFalse(CleartextPolicy.isPrivateHost("192.168.1.256"))
        assertFalse(CleartextPolicy.isPrivateHost("10.0.0.1.5"))
    }
}
