package com.librechat.android.feature.agents.di

import com.librechat.android.feature.agents.util.AndroidContentReader
import com.librechat.android.feature.agents.util.ContentReader
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val agentsPlatformModule: Module = module {
    single { AndroidContentReader(androidContext()) } bind ContentReader::class
}
