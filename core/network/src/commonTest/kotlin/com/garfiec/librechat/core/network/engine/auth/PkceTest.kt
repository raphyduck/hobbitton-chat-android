package com.garfiec.librechat.core.network.engine.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PkceTest {

    /**
     * RFC 7636 Appendix B, verbatim. If this passes, the digest, the encoding and the padding rule
     * are all right at once — and Authelia will accept the challenge for the reason that matters:
     * it is what the spec says, not what we thought it said.
     */
    @Test
    fun `challenge matches the RFC 7636 test vector`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pkceChallengeOf(verifier))
    }

    @Test
    fun `challenge carries no padding and no URL-hostile character`() {
        val challenge = pkceChallengeOf("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")

        assertTrue(challenge.none { it == '=' || it == '+' || it == '/' }, challenge)
    }

    @Test
    fun `generated verifier is 43 characters of the unreserved set`() {
        val pair = generatePkcePair { count -> ByteArray(count) { it.toByte() } }

        // RFC 7636 §4.1 fixes the alphabet: anything outside it would be re-encoded in transit and
        // the verifier would stop matching its own challenge.
        val unreserved = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~')
        assertEquals(43, pair.verifier.length)
        assertTrue(pair.verifier.all { it in unreserved }, pair.verifier)
    }

    @Test
    fun `generated pair is internally consistent`() {
        val pair = generatePkcePair { count -> ByteArray(count) { (it * 7).toByte() } }

        assertEquals(pkceChallengeOf(pair.verifier), pair.challenge)
    }

    @Test
    fun `two calls do not produce the same verifier`() {
        // Not a randomness test — a wiring test. A verifier cached at class level, or a `Random`
        // seeded once, would sail through every assertion above and hand the token to whoever
        // intercepts the redirect.
        assertNotEquals(generatePkcePair().verifier, generatePkcePair().verifier)
    }
}
