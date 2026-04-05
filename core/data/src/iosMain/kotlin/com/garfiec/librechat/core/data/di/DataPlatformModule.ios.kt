package com.garfiec.librechat.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.data.datastore.IosTokenDataStore
import com.garfiec.librechat.core.data.datastore.ServerUrlKeychainFallback
import com.garfiec.librechat.core.data.db.LibreChatDatabase
import com.garfiec.librechat.core.data.repository.CommonSessionCacheCleaner
import com.garfiec.librechat.core.data.repository.SessionCacheCleaner
import com.garfiec.librechat.core.network.client.SecureTokenStorage
import com.garfiec.librechat.core.network.client.TokenManager
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.binds
import org.koin.dsl.module
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun ensureDirectoryExists(path: String) {
    NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
}

actual val dataPlatformModule: Module = module {

    // --- Database ---
    single {
        val dbDir = NSHomeDirectory() + "/Library/Application Support"
        ensureDirectoryExists(dbDir)
        Room.databaseBuilder<LibreChatDatabase>(name = "$dbDir/librechat.db")
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    // --- DataStore ---
    single<DataStore<Preferences>> {
        val datastoreDir = NSHomeDirectory() + "/Library/Application Support/datastore"
        ensureDirectoryExists(datastoreDir)
        val path = "$datastoreDir/$DATASTORE_FILE_NAME.preferences_pb"
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
