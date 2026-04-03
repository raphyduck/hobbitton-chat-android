package com.garfiec.librechat.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.data.datastore.TokenDataStore
import com.garfiec.librechat.core.data.db.LibreChatDatabase
import com.garfiec.librechat.core.data.repository.CommonSessionCacheCleaner
import com.garfiec.librechat.core.data.repository.SessionCacheCleaner
import com.garfiec.librechat.core.network.client.SecureTokenStorage
import com.garfiec.librechat.core.network.client.TokenManager
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.binds
import org.koin.dsl.module

private val android.content.Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_FILE_NAME,
)

actual val dataPlatformModule: Module = module {

    // --- Database ---
    single {
        Room.databaseBuilder(androidContext(), LibreChatDatabase::class.java, "librechat.db").build()
    }

    // --- DataStore ---
    single<DataStore<Preferences>> { androidContext().settingsDataStore }

    // --- Token Storage ---
    single {
        TokenDataStore(
            context = androidContext(),
            refreshClient = lazy(LazyThreadSafetyMode.NONE) { get<HttpClient>(KoinQualifiers.Refresh) },
        )
    } binds arrayOf(TokenManager::class, SecureTokenStorage::class)

    // --- Session Cache Cleaner ---
    single<SessionCacheCleaner> { CommonSessionCacheCleaner(androidContext().cacheDir.absolutePath) }
}
