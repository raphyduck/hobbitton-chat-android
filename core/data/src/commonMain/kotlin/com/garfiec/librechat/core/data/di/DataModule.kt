package com.garfiec.librechat.core.data.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.data.datastore.GlobalProfileStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.datastore.ThemeDataStore
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.module

expect val dataPlatformModule: Module

val dataModule = module {

    includes(dataPlatformModule)
    includes(hobbittonDataModule)

    // --- Datastores ---

    single {
        ThemeDataStore(
            dataStore = get(),
            appScope = get<CoroutineScope>(KoinQualifiers.ApplicationScope),
            ioDispatcher = get(KoinQualifiers.IO),
        )
    }
    single {
        SettingsDataStore(
            dataStore = get(),
            appScope = get<CoroutineScope>(KoinQualifiers.ApplicationScope),
            ioDispatcher = get(KoinQualifiers.IO),
        )
    }
    single { GlobalProfileStore(dataStore = get()) }
}
