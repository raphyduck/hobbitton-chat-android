package com.garfiec.librechat.core.network.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import okhttp3.Dispatcher
import org.koin.core.module.Module
import org.koin.dsl.module

actual val networkPlatformModule: Module = module {
    single<HttpClientEngineFactory<*>> { OkHttpWithRoom }
}

/**
 * Requests in flight to one host at once (25/10/2026). OkHttp's default is 5, and the engine's
 * client queues everything past it, silently.
 *
 * Two of those five were held for good by the event feeds (the attention watcher's and the open
 * conversation's), and opening a conversation sends the transcript, the models, the session's
 * connectors, its questions and the drawer's refresh at once, while the conversation left behind
 * still closes its feed. The transcript waited in OkHttp's queue past the screen's 10 s and was
 * dropped, three times: the conversation stayed on its placeholders until it was opened again
 * (Raphaël, 25/10/2026, the engine itself answering in a hundredth of a second).
 */
internal const val MAX_REQUESTS_PER_HOST = 32
internal const val MAX_REQUESTS = 64

/** OkHttp, each client with its own dispatcher sized by [MAX_REQUESTS_PER_HOST]. */
internal object OkHttpWithRoom : HttpClientEngineFactory<OkHttpConfig> {
    override fun create(block: OkHttpConfig.() -> Unit): HttpClientEngine = OkHttp.create {
        config {
            dispatcher(
                Dispatcher().apply {
                    maxRequests = MAX_REQUESTS
                    maxRequestsPerHost = MAX_REQUESTS_PER_HOST
                },
            )
        }
        block()
    }
}
