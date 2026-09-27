package com.garfiec.librechat.core.data.portal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where the single sign-in stops the web view (D-076). The one hop that matters is the chat's
 * return out of `/oauth`: missed, the web client loads and spends the refresh token itself; caught
 * too early, the cookie is not set yet.
 */
class PortalNavigationTest {

    private val chat = "https://chat.example.com"

    private fun classify(url: String, base: String = chat) = classifyPortalNavigation(url, base)

    @Test
    fun `the round trip's own pages load`() {
        assertEquals(PortalNavigation.Load, classify("https://chat.example.com/oauth/openid"))
        assertEquals(PortalNavigation.Load, classify("https://chat.example.com/oauth/openid/callback?code=c&state=s"))
        assertEquals(PortalNavigation.Load, classify("https://auth.example.com/?rd=https%3A%2F%2Fchat.example.com%2Foauth%2Fopenid"))
        assertEquals(PortalNavigation.Load, classify("https://auth.example.com/api/oidc/authorization?client_id=x"))
        assertEquals(PortalNavigation.Load, classify("https://sched.example.com/oauth/authelia"))
    }

    @Test
    fun `leaving oauth on the chat server is the return`() {
        assertEquals(PortalNavigation.ChatReturned, classify("https://chat.example.com/"))
        assertEquals(PortalNavigation.ChatReturned, classify("https://chat.example.com"))
        assertEquals(PortalNavigation.ChatReturned, classify("https://chat.example.com/c/new"))
        // The failure path ends on the chat too: the cookie's absence says it failed, not the URL.
        assertEquals(PortalNavigation.ChatReturned, classify("https://chat.example.com/login"))
        assertEquals(PortalNavigation.ChatReturned, classify("https://CHAT.example.com:443/"))
    }

    @Test
    fun `a path that merely starts with oauth is not under it`() {
        assertEquals(PortalNavigation.ChatReturned, classify("https://chat.example.com/oauthentic"))
    }

    @Test
    fun `another scheme or port on the chat's host is not the chat`() {
        assertEquals(PortalNavigation.Load, classify("http://chat.example.com/"))
        assertEquals(PortalNavigation.Load, classify("https://chat.example.com:8443/"))
    }

    @Test
    fun `a server under a path prefix is judged relative to it`() {
        val base = "https://example.com/librechat"
        assertEquals(PortalNavigation.Load, classify("https://example.com/librechat/oauth/openid/callback", base))
        assertEquals(PortalNavigation.ChatReturned, classify("https://example.com/librechat/", base))
    }

    @Test
    fun `the app's callback is handed over, other schemes are refused`() {
        assertEquals(PortalNavigation.AppCallback, classify("at.hobbitton.chat://oauth?code=c&state=s"))
        assertEquals(PortalNavigation.AppCallback, classify("AT.HOBBITTON.CHAT://oauth?error=access_denied"))
        assertEquals(PortalNavigation.Refused, classify("intent://scan/#Intent;scheme=zxing;end"))
        assertEquals(PortalNavigation.Refused, classify("javascript:alert(1)"))
        assertEquals(PortalNavigation.Refused, classify("file:///sdcard/secret"))
    }

    @Test
    fun `the callback test is a scheme test, not a substring one`() {
        assertTrue(isPortalCallback("at.hobbitton.chat://oauth"))
        assertFalse(isPortalCallback("https://evil.example.net/?next=at.hobbitton.chat://oauth"))
    }
}
