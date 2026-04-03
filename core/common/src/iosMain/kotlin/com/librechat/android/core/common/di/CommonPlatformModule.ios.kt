package com.librechat.android.core.common.di

import com.librechat.android.core.common.network.ConnectivityObserver
import com.librechat.android.core.common.network.IosConnectivityObserver
import org.koin.core.module.Module
import org.koin.dsl.module

actual val commonPlatformModule: Module = module {
    single<ConnectivityObserver> { IosConnectivityObserver() }
}
