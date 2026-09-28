package com.garfiec.librechat.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.data.legacy.LegacyLibreChatCleanup
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_FILE_NAME,
    corruptionHandler = settingsCorruptionHandler(),
)

actual val dataPlatformModule: Module = module {

    // --- DataStore ---
    single<DataStore<Preferences>> { androidContext().settingsDataStore }

    // --- What LibreChat left on the device (D-077), removed once at start ---
    single {
        LegacyLibreChatCleanup(
            context = androidContext(),
            dataStore = get(),
            appScope = get<CoroutineScope>(KoinQualifiers.ApplicationScope),
            ioDispatcher = get(KoinQualifiers.IO),
        )
    }
}
