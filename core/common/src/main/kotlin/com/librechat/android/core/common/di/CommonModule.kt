package com.librechat.android.core.common.di

import com.librechat.android.core.common.network.ConnectivityObserver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val commonModule = module {
    // Dispatchers
    single<CoroutineDispatcher>(KoinQualifiers.IO) { Dispatchers.IO }
    single<CoroutineDispatcher>(KoinQualifiers.Default) { Dispatchers.Default }
    single<CoroutineDispatcher>(KoinQualifiers.Main) { Dispatchers.Main }

    // Note: This scope lives for the entire app process lifetime.
    // Coroutines launched here persist across logout/login cycles.
    // If session-scoped work is needed, use a dedicated session scope instead.
    single<CoroutineScope>(KoinQualifiers.ApplicationScope) {
        CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(KoinQualifiers.Default))
    }

    // Connectivity
    single { ConnectivityObserver(androidContext()) }
}
