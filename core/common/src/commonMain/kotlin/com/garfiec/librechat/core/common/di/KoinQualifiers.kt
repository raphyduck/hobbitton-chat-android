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

    // Single-thread dispatcher dedicated to the persistent diagnostic log sink, so all
    // file appends/rotation happen on one thread (no locks, no rotation races).
    val LogWriter = named("logWriter")
}
