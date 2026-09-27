package com.garfiec.librechat.core.common.di

import org.koin.core.qualifier.named

object KoinQualifiers {
    val IO = named("io")
    val Default = named("default")
    val Main = named("main")
    val ApplicationScope = named("applicationScope")
    val Streaming = named("streaming")
    val Refresh = named("refresh")

    /**
     * The Agent engine's client. A second client, not a variant of the chat's: different host,
     * different credentials, and none of the chat's account-switch machinery applies to it.
     */
    val Engine = named("engine")

    /**
     * The scheduler's client. A **third** one, and for the same reason as the engine's: another
     * host, and only one of the engine's two credentials applies to it. The scheduler has no Basic
     * of its own — Authelia is all that guards it — so sending the engine's would be handing a
     * secret to a host that never asked for it.
     */
    val Scheduler = named("scheduler")

    /**
     * The portal's OAuth client — a **bare** one (M1, 26/09/2026). The token endpoint receives the
     * PKCE verifier and the portal's refresh token, and nothing else: no LibreChat bearer, no
     * gateway headers, no account-switch barrier. Built on the chat's client, the portal's back
     * channel would inherit all three, and on a deployment where the portal and LibreChat share a
     * host the chat's session bearer would ride along to the portal's token endpoint.
     */
    val Portal = named("portal")

    // Single-thread dispatcher dedicated to the persistent diagnostic log sink, so all
    // file appends/rotation happen on one thread (no locks, no rotation races).
    val LogWriter = named("logWriter")
}
