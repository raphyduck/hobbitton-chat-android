package com.librechat.android.feature.files.di

import com.librechat.android.feature.files.platform.FileReader
import com.librechat.android.feature.files.platform.IosFileReader
import org.koin.core.module.Module
import org.koin.dsl.module

actual val filesPlatformModule: Module = module {
    single<FileReader> { IosFileReader() }
}
