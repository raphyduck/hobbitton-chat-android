package com.librechat.android.feature.files.di

import com.librechat.android.feature.files.viewmodel.FilesViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

expect val filesPlatformModule: Module

val filesModule = module {
    includes(filesPlatformModule)
    viewModelOf(::FilesViewModel)
}
