package com.garfiec.librechat.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the OTP field keeps — which is the difference between a code that pastes and one that does
 * not.
 *
 * The rule is SANITIZE, never validate. A field that rejected anything impure would drop a paste
 * whole and silently: authenticator apps copy « 123 456 » with a space, and a clipboard commonly
 * carries a trailing newline.
 */
class OtpCodeInputTest {

    @Test
    fun `a plain code is kept as is`() {
        assertEquals("123456", sanitizeOtp("123456"))
    }

    @Test
    fun `the space an authenticator copies is dropped, not the code`() {
        assertEquals("123456", sanitizeOtp("123 456"))
    }

    @Test
    fun `a trailing newline from the clipboard is dropped`() {
        assertEquals("123456", sanitizeOtp("123456\n"))
    }

    @Test
    fun `anything longer than the code is cut, not refused`() {
        assertEquals("123456", sanitizeOtp("1234567890"))
    }

    @Test
    fun `letters are removed rather than rejecting the whole paste`() {
        assertEquals("123456", sanitizeOtp("code: 123456"))
    }

    @Test
    fun `an entry with no digit at all yields nothing`() {
        assertEquals("", sanitizeOtp("abc"))
    }
}
