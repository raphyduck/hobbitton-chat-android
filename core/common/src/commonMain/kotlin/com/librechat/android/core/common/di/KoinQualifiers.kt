package com.librechat.android.core.common.di

import org.koin.core.qualifier.named

object KoinQualifiers {
    val IO = named("io")
    val Default = named("default")
    val Main = named("main")
    val ApplicationScope = named("applicationScope")
    val Streaming = named("streaming")
    val Refresh = named("refresh")
}
