package com.garfiec.librechat.core.data.engine

/**
 * What the device keeps outside the stores and must forget at sign-out (26/10/2026, the review):
 * the attachments opened from conversations (decoded under the cache directory for the viewer),
 * the image cache, the diagnostic log. The stores clear themselves; this is the platform's part,
 * bound by `:app`.
 */
fun interface DeviceCleanup {
    suspend fun clear()
}
