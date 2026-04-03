package com.librechat.android

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.librechat.android.core.common.di.commonModule
import com.librechat.android.core.data.di.dataModule
import com.librechat.android.core.network.di.networkModule
import com.librechat.android.feature.agents.di.agentsModule
import com.librechat.android.feature.auth.di.authModule
import com.librechat.android.feature.auth.di.authPlatformModule
import com.librechat.android.feature.chat.di.chatModule
import com.librechat.android.feature.conversations.di.conversationsModule
import com.librechat.android.feature.files.di.filesModule
import com.librechat.android.feature.settings.di.settingsModule
import com.librechat.android.navigation.appModule
import io.ktor.client.HttpClient
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import co.touchlab.kermit.Logger

class LibreChatApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        try {
            startKoin {
                if (BuildConfig.DEBUG) {
                    androidLogger(org.koin.core.logger.Level.DEBUG)
                }
                androidContext(this@LibreChatApplication)
                allowOverride(false)
                modules(
                    commonModule,
                    networkModule,
                    dataModule,
                    appModule,
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
        val httpClient: HttpClient = get()

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
