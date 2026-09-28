package com.garfiec.librechat.core.common.di

import org.koin.core.qualifier.named

object KoinQualifiers {
    val IO = named("io")
    val Default = named("default")
    val Main = named("main")
    val ApplicationScope = named("applicationScope")

    /** The Agent engine's client: the portal's bearer, scoped to the engine's host. */
    val Engine = named("engine")

    /** The scheduler's client: the same bearer, scoped to the scheduler's host. */
    val Scheduler = named("scheduler")

    /**
     * The portal's OAuth client — a **bare** one (M1, 26/09/2026). The token endpoint receives the
     * PKCE verifier and the portal's refresh token, and nothing else: no bearer of another service.
     */
    val Portal = named("portal")

    // Single-thread dispatcher dedicated to the persistent diagnostic log sink, so all
    // file appends/rotation happen on one thread (no locks, no rotation races).
    val LogWriter = named("logWriter")
}
