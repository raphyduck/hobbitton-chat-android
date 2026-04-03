package com.garfiec.librechat.shared

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import io.ktor.client.HttpClient
import org.koin.core.Koin

/**
 * Swift-callable accessors for Koin-managed singletons.
 * The Koin instance is stored here by startIosKoin() and used
 * by Swift via typed helper functions.
 *
 * All functions use @Throws to ensure Kotlin exceptions propagate
 * as NSError to Swift rather than causing SIGABRT via
 * trapOnUndeclaredException.
 */
object IosKoinAccessor {

    internal lateinit var koin: Koin

    @Throws(Exception::class)
    fun getSDK(): LibreChatSDK = koin.get()

    @Throws(Exception::class)
    fun getServerDataStore(): ServerDataStore = koin.get()

    @Throws(Exception::class)
    fun getStreamingHttpClient(): HttpClient =
        koin.get(qualifier = KoinQualifiers.Streaming)

    @Throws(Exception::class)
    fun getAuthRepository(): AuthRepository = koin.get()

    @Throws(Exception::class)
    fun getFileRepository(): FileRepository = koin.get()

    @Throws(Exception::class)
    fun getConfigRepository(): ConfigRepository = koin.get()
}
