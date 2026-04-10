package com.garfiec.librechat

import android.app.Application
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.garfiec.librechat.core.common.di.commonModule
import com.garfiec.librechat.core.data.di.dataModule
import com.garfiec.librechat.core.network.di.networkModule
import com.garfiec.librechat.feature.agents.di.agentsModule
import com.garfiec.librechat.feature.auth.di.authModule
import com.garfiec.librechat.feature.auth.di.authPlatformModule
import com.garfiec.librechat.feature.chat.di.chatModule
import com.garfiec.librechat.feature.conversations.di.conversationsModule
import com.garfiec.librechat.feature.files.di.filesModule
import com.garfiec.librechat.feature.settings.di.settingsModule
import com.garfiec.librechat.shared.navigation.sharedAppModule
import io.ktor.client.HttpClient
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class LibreChatApplication : Application(), SingletonImageLoader.Factory {

    private val httpClient: HttpClient by inject()

    override fun onCreate() {
        super.onCreate()
        try {
            startKoin {
                if (BuildConfig.DEBUG) {
                    androidLogger(Level.DEBUG)
                }
                androidContext(this@LibreChatApplication)
                allowOverride(false)
                modules(
                    commonModule,
                    networkModule,
                    dataModule,
                    sharedAppModule,
                    authModule,
                    authPlatformModule,
                    chatModule,
                    conversationsModule,
                    settingsModule,
                    agentsModule,
                    filesModule,
                )
            }
        } catch (e: Exception) {
            Logger.e(e) { "Koin initialization failed" }
            throw e // Always rethrow — DI failure is unrecoverable
        }
    }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory(httpClient))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024) // 250 MB
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
