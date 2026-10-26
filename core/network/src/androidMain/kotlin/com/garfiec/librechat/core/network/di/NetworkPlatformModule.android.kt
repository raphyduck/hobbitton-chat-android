package com.garfiec.librechat.core.network.di

import com.garfiec.librechat.core.network.engine.ConnectionReset
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

actual val networkPlatformModule: Module = module {
    single<HttpClientEngineFactory<*>> { OkHttpWithRoom }
    single<ConnectionReset> { OkHttpConnections }
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

/**
 * HTTP/2 PINGs on every connection, this often. A connection that misses a pong is shut down by
 * OkHttp and everything on it fails at once, the event feeds included, which reconnect: the only
 * way a connection left dead by a sleep or a change of network is noticed before a request hangs
 * on it (26/10/2026, see [ConnectionReset]). Ktor sets none.
 */
internal const val PING_INTERVAL_SECONDS = 20L

/**
 * OkHttp, each client with its own dispatcher sized by [MAX_REQUESTS_PER_HOST], its own connection
 * pool and PINGs every [PING_INTERVAL_SECONDS], both registered with [OkHttpConnections] so that
 * every connection can be dropped at once. Ktor builds one OkHttpClient per timeout profile from
 * the same configuration (and replaces the dispatcher before applying it), so the pool and the
 * dispatcher are made once here, outside the block, and shared by that client's variants.
 */
internal object OkHttpWithRoom : HttpClientEngineFactory<OkHttpConfig> {
    override fun create(block: OkHttpConfig.() -> Unit): HttpClientEngine {
        val dispatcher = Dispatcher().apply {
            maxRequests = MAX_REQUESTS
            maxRequestsPerHost = MAX_REQUESTS_PER_HOST
        }
        val pool = ConnectionPool()
        OkHttpConnections.register(dispatcher, pool)
        return OkHttp.create {
            config {
                dispatcher(dispatcher)
                connectionPool(pool)
                pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            }
            block()
        }
    }
}

/**
 * Every client's dispatcher and pool, to drop all their connections ([ConnectionReset]). Cancelling
 * the calls first is what frees the connections: a pool evicts idle connections only, and the one
 * to get rid of carries the event feeds, which never go idle.
 */
internal object OkHttpConnections : ConnectionReset {
    private val held = mutableListOf<Pair<Dispatcher, ConnectionPool>>()

    @Synchronized
    fun register(dispatcher: Dispatcher, pool: ConnectionPool) {
        held += dispatcher to pool
    }

    /** Sockets close here: on the IO dispatcher, never on the thread that asked. */
    override suspend fun dropAll() = withContext(Dispatchers.IO) {
        val snapshot = synchronized(this@OkHttpConnections) { held.toList() }
        snapshot.forEach { (dispatcher, _) -> dispatcher.cancelAll() }
        snapshot.forEach { (_, pool) -> pool.evictAll() }
    }
}
