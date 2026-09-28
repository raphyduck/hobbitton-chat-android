package com.garfiec.librechat.core.data.portal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where the sign-in stops the web view (D-076, D-077): the portal's pages load, the scheduler's hop
 * to the app scheme is handed to the mailbox, and every other scheme is refused.
 */
class PortalNavigationTest {

    @Test
    fun `the round trip's own pages load`() {
        assertEquals(PortalNavigation.Load, classifyPortalNavigation("https://auth.example.com/api/oidc/authorization?client_id=x"))
        assertEquals(PortalNavigation.Load, classifyPortalNavigation("https://auth.example.com/?rd=x"))
        assertEquals(PortalNavigation.Load, classifyPortalNavigation("https://sched.example.com/oauth/authelia"))
        // A private address may be plain http — the address form already refused a public one.
        assertEquals(PortalNavigation.Load, classifyPortalNavigation("http://192.168.1.10:9091/"))
    }

    @Test
    fun `there is no chat server to stop on any more`() {
        // Before D-077 the chat's origin outside `/oauth` stopped the view to read a cookie. The
        // chat is the engine now: any https page is just a page.
        assertEquals(PortalNavigation.Load, classifyPortalNavigation("https://chat.example.com/"))
    }

    @Test
    fun `the app's callback is handed over, other schemes are refused`() {
        assertEquals(PortalNavigation.AppCallback, classifyPortalNavigation("at.hobbitton.chat://oauth?code=c&state=s"))
        assertEquals(PortalNavigation.AppCallback, classifyPortalNavigation("AT.HOBBITTON.CHAT://oauth?error=access_denied"))
        assertEquals(PortalNavigation.Refused, classifyPortalNavigation("intent://scan/#Intent;scheme=zxing;end"))
        assertEquals(PortalNavigation.Refused, classifyPortalNavigation("javascript:alert(1)"))
        assertEquals(PortalNavigation.Refused, classifyPortalNavigation("file:///sdcard/secret"))
    }

    @Test
    fun `the callback test is a scheme test, not a substring one`() {
        assertTrue(isPortalCallback("at.hobbitton.chat://oauth"))
        assertFalse(isPortalCallback("https://evil.example.net/?next=at.hobbitton.chat://oauth"))
    }
}
