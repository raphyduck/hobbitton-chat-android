package com.garfiec.librechat.core.network.engine.auth

/**
 * The portal refused the grant itself: the refresh token is spent, revoked, or was issued to
 * another client. Going back through the portal is the only way out.
 *
 * It exists to be **distinguishable from a failure to reach the portal at all**, because the two
 * demand opposite reactions and the difference is invisible once both are a `Throwable`:
 *
 * - refused → forget the pair; keeping it reproduces the same refusal on every later call;
 * - unreachable → keep it; the tokens are still perfectly valid, and the phone merely lost its
 *   network for a second.
 *
 * Treating the second as the first is how a lost Wi-Fi second becomes a full second-factor login —
 * with nothing on screen to say that is what happened.
 */
class EngineGrantRefused(
    /** The OAuth error code when the portal named one — `invalid_grant`, and friends. */
    val error: String,
    /** What the HTTP layer raised, kept so the status and body survive into the log. */
    cause: Throwable? = null,
) : Exception("The portal refused the refresh grant: $error", cause)
