package com.librechat.android.feature.files.di

import com.librechat.android.feature.files.platform.AndroidFileReader
import com.librechat.android.feature.files.platform.FileReader
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.dsl.module

actual val filesPlatformModule: Module = module {
    single<FileReader> { AndroidFileReader(androidApplication()) }
}
