package com.librechat.android.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.librechat.android.core.common.di.KoinQualifiers
import com.librechat.android.core.data.datastore.IosTokenDataStore
import com.librechat.android.core.data.datastore.ServerUrlKeychainFallback
import com.librechat.android.core.data.db.LibreChatDatabase
import com.librechat.android.core.data.repository.CommonSessionCacheCleaner
import com.librechat.android.core.data.repository.SessionCacheCleaner
import com.librechat.android.core.network.client.SecureTokenStorage
import com.librechat.android.core.network.client.TokenManager
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.binds
import org.koin.dsl.module
import platform.Foundation.NSHomeDirectory

actual val dataPlatformModule: Module = module {

    // --- Database ---
    single {
        val dbPath = NSHomeDirectory() + "/Library/Application Support/librechat.db"
        Room.databaseBuilder<LibreChatDatabase>(name = dbPath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    // --- DataStore ---
    single<DataStore<Preferences>> {
        val path = NSHomeDirectory() + "/Library/Application Support/datastore/$DATASTORE_FILE_NAME.preferences_pb"
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { path.toPath() },
        )
    }

    // --- Token Storage ---
    single {
        IosTokenDataStore(
            refreshClient = lazy(LazyThreadSafetyMode.NONE) { get<HttpClient>(KoinQualifiers.Refresh) },
        )
    } binds arrayOf(TokenManager::class, SecureTokenStorage::class, ServerUrlKeychainFallback::class)

    // --- Session Cache Cleaner ---
    single<SessionCacheCleaner> {
        @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
        val cachePath = platform.Foundation.NSSearchPathForDirectoriesInDomains(
            platform.Foundation.NSCachesDirectory,
            platform.Foundation.NSUserDomainMask,
            true,
        ).firstOrNull() as? String ?: error("Unable to resolve NSCachesDirectory")
        CommonSessionCacheCleaner(cachePath)
    }
}
