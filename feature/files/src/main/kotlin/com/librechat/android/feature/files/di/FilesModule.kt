package com.librechat.android.feature.files.di

import com.librechat.android.feature.files.viewmodel.FilesViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val filesModule = module {
    viewModel {
        FilesViewModel(
            fileRepository = get(),
            context = androidApplication(),
            serverDataStore = get(),
        )
    }
}
