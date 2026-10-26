package com.garfiec.librechat.core.data.engine

/**
 * The platform's part of a sign-in and a sign-out (26/10/2026, the review). Bound by `:app`.
 *
 * Signed in: the push service starts (it follows the server's notifications while the app is out
 * of sight). Signed out: the push service stops, and what the device keeps outside the stores is
 * forgotten: the attachments opened from conversations (decoded under the cache directory for the
 * viewer), the image cache, the diagnostic log. The stores clear themselves.
 */
interface DeviceSession {
    fun onSignedIn()
    suspend fun onSignedOut()
}
