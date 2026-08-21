package com.garfiec.librechat.core.network.engine.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(count: Int): ByteArray {
    val bytes = ByteArray(count)
    val status = bytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, count.convert(), pinned.addressOf(0))
    }
    // Failing loudly beats returning weak bytes: a verifier nobody can guess is the whole
    // security argument of a public OAuth client.
    check(status == errSecSuccess) { "SecRandomCopyBytes failed: $status" }
    return bytes
}
