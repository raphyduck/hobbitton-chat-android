package com.garfiec.librechat.core.network.engine.auth

import okio.ByteString.Companion.toByteString

/**
 * PKCE (RFC 7636) for the Agent engine's authorization code flow.
 *
 * The engine sits behind Authelia, which issues the access token this client presents. Authelia
 * registers this app as a *public* client: an installed app cannot keep a client secret — anyone
 * can unzip the APK — so the proof of possession is manufactured per exchange instead of stored.
 * The verifier is generated, kept in memory, and swapped for the token; only its SHA-256 digest
 * ever travels through the browser.
 */
public data class PkcePair(
    /** The secret. Sent only on the back channel, at the token endpoint. */
    val verifier: String,
    /** BASE64URL(SHA256(verifier)). Sent through the browser, where it is useless on its own. */
    val challenge: String,
) {
    public companion object {
        /** The only method Authelia is configured to accept; `plain` defeats the point. */
        public const val METHOD: String = "S256"
    }
}

/**
 * 32 bytes → 43 base64url characters, the low end of RFC 7636 §4.1's 43–128 range and the length
 * the RFC's own example uses. More would not add entropy: the digest is 256 bits regardless.
 */
private const val VERIFIER_BYTES = 32

/**
 * Platform CSPRNG. `kotlin.random.Random` is explicitly *not* one — it is seeded predictably and a
 * guessable verifier hands the token to whoever intercepts the redirect.
 */
internal expect fun secureRandomBytes(count: Int): ByteArray

/**
 * Fresh verifier/challenge pair. [random] is injectable so tests can pin the bytes; production
 * callers never pass it.
 */
public fun generatePkcePair(random: (Int) -> ByteArray = ::secureRandomBytes): PkcePair {
    val verifier = base64UrlNoPad(random(VERIFIER_BYTES))
    return PkcePair(verifier = verifier, challenge = pkceChallengeOf(verifier))
}

/** BASE64URL-ENCODE(SHA256(ASCII(verifier))), per RFC 7636 §4.2. */
public fun pkceChallengeOf(verifier: String): String =
    base64UrlNoPad(verifier.encodeToByteArray().toByteString().sha256().toByteArray())

/**
 * base64url without padding. The `=` padding is not merely optional here — RFC 7636 §A says to
 * strip it, and Authelia rejects a challenge that carries it.
 */
internal fun base64UrlNoPad(bytes: ByteArray): String =
    bytes.toByteString().base64Url().trimEnd('=')
