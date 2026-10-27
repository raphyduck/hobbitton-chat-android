package com.garfiec.librechat.core.network.di

import com.garfiec.librechat.core.common.di.AppThreads
import com.garfiec.librechat.core.network.engine.ConnectionReset
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import java.net.InetAddress
import java.net.Socket
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import org.koin.core.module.Module
import org.koin.dsl.module

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
 *
 * Ktor's side of each call runs on [AppThreads.net] (27/10/2026), not on `Dispatchers.IO`: its
 * OkHttp engine reads every response body with a blocking read, a thread held as long as the body
 * flows or stalls (the event feed's, for good). On the shared IO threads, those reads competed with
 * everything else the process does there; on the app's elastic pool, a stalled body costs one
 * thread and never another request. Every socket goes through [OkHttpConnections.sockets], so a
 * drop can close it for real.
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
                socketFactory(OkHttpConnections.sockets)
            }
            block()
            this.dispatcher = AppThreads.net
        }
    }
}

/**
 * Every client's dispatcher, pool and socket, to drop all their connections ([ConnectionReset]).
 *
 * Cancelling the calls and evicting the pools is not enough on a dead connection (27/10/2026): a
 * pool evicts idle connections only, the one to get rid of carries the event feed and never goes
 * idle, and a cancelled HTTP/2 stream only queues its reset behind the connection's writer, which a
 * dead socket can hold for minutes (the feed's has no write timeout). Closing the sockets
 * themselves fails every stream on them at once, unblocks whatever thread waits on them, and
 * leaves the pool nothing to hand the next request but a fresh connection.
 */
internal object OkHttpConnections : ConnectionReset {
    private val held = mutableListOf<Pair<Dispatcher, ConnectionPool>>()

    /** The sockets of every client, as OkHttp opens them; closed ones fall out on their own. */
    val sockets = TrackedSockets()

    @Synchronized
    fun register(dispatcher: Dispatcher, pool: ConnectionPool) {
        held += dispatcher to pool
    }

    /** Where the closing runs: the app's IO threads, never the caller's, and never waited on for long. */
    private val closing = CoroutineScope(SupervisorJob() + AppThreads.io)

    /**
     * Sockets close on the app's IO threads, never on the thread that asked; the caller waits for
     * them [DROP_WAIT_MS] at most (27/10/2026): closing what a dead link holds can block in the
     * platform, and a retry must not wait on it. What is left finishes in the background.
     */
    override suspend fun dropAll() {
        val done = closing.launch {
            val snapshot = synchronized(this@OkHttpConnections) { held.toList() }
            snapshot.forEach { (dispatcher, _) -> dispatcher.cancelAll() }
            sockets.closeAll()
            snapshot.forEach { (_, pool) -> pool.evictAll() }
        }
        withTimeoutOrNull(DROP_WAIT_MS) { done.join() }
    }

    private const val DROP_WAIT_MS = 1_500L
}

/**
 * The platform's sockets, each one remembered (weakly) until [closeAll]. OkHttp layers TLS over
 * the socket this factory hands it: closing this one closes the connection under the TLS session
 * without the close_notify a TLS close would try to write into a dead link.
 */
internal class TrackedSockets(private val platform: SocketFactory = SocketFactory.getDefault()) : SocketFactory() {
    private val open: MutableSet<Socket> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    private fun Socket.tracked(): Socket = also { open += it }

    override fun createSocket(): Socket = platform.createSocket().tracked()

    override fun createSocket(host: String?, port: Int): Socket = platform.createSocket(host, port).tracked()

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        platform.createSocket(host, port, localHost, localPort).tracked()

    override fun createSocket(host: InetAddress?, port: Int): Socket = platform.createSocket(host, port).tracked()

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        platform.createSocket(address, port, localAddress, localPort).tracked()

    /** How many are still open: what the thread report prints next to the network threads. */
    val openCount: Int get() = synchronized(open) { open.count { !it.isClosed } }

    fun closeAll() {
        val all = synchronized(open) { open.toList().also { open.clear() } }
        all.forEach { socket -> runCatching { socket.close() } }
    }
}
