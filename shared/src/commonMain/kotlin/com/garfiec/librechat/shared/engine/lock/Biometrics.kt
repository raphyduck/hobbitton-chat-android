package com.garfiec.librechat.shared.engine.lock

import androidx.compose.runtime.Composable

/** How a fingerprint request ended. */
enum class BiometricOutcome {
    SUCCESS,
    CANCELLED,

    /** The phone's fingerprints changed since the fingerprint was turned on: its key is gone. */
    INVALIDATED,
}

/**
 * The phone's fingerprint for the lock (28/10/2026): a strong biometric only, and bound to a
 * keystore key the phone voids when a fingerprint is added, as banking apps do, so a finger
 * enrolled later by someone else never opens Eva.
 */
interface Biometrics {
    /** Whether the phone has a strong biometric set up. */
    val available: Boolean

    /** Creates the key the fingerprint unlocks; false when the phone refuses. */
    fun enroll(): Boolean

    /** Asks for the fingerprint over the key; [onResult] runs on the main thread. */
    fun authenticate(title: String, cancel: String, onResult: (BiometricOutcome) -> Unit)

    /** Deletes the key: the fingerprint is off. */
    fun forget()
}

/** The phone's fingerprint, or null where the platform has none to offer. */
@Composable
expect fun rememberBiometrics(): Biometrics?
