package com.garfiec.librechat.core.data.engine

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.network.engine.auth.FormPostCallback
import com.garfiec.librechat.core.network.engine.auth.callbackResponsePage
import com.garfiec.librechat.core.network.engine.auth.parseFormPostCallback
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The loopback socket that catches Authelia's form POST, alive for exactly one request.
 *
 * Written against a raw socket rather than an embedded HTTP server because that is the whole of the
 * job: accept once, read one request, answer once, close. Pulling in a server for it would add a
 * dependency, a lifecycle and a thread pool to something that must not outlive a single exchange.
 *
 * Three properties are not incidental and should survive any rewrite:
 *
 *  * **Bound to the loopback interface explicitly.** `ServerSocket(0)` alone listens on every
 *    interface, which would put an authorization callback on the Wi-Fi network for as long as the
 *    sign-in lasts.
 *  * **Port 0.** The OS picks; the app cannot reserve a port and nothing would guarantee one. The
 *    client is registered with a port-less `http://127.0.0.1/oauth/authelia` precisely so that any
 *    port is acceptable at redemption time (RFC 8252 §7.3).
 *  * **Backlog of one.** Nothing legitimate queues behind the callback.
 */
class SocketCallbackListener(
    private val io: CoroutineDispatcher,
) : EngineCallbackListener {

    private var socket: ServerSocket? = null

    override suspend fun open(): Int = withContext(io) {
        val bound = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))
        socket = bound
        bound.localPort
    }

    override suspend fun await(timeoutMillis: Long): FormPostCallback = withContext(io) {
        val bound = socket ?: return@withContext FormPostCallback.Malformed("socket not open")
        bound.soTimeout = timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        try {
            bound.accept().use { connection ->
                connection.soTimeout = READ_TIMEOUT_MS
                // Read what is available rather than to end-of-stream: the browser keeps the
                // connection open after the body, so reading until EOF would block until the read
                // timeout on every successful sign-in.
                val buffer = ByteArray(MAX_REQUEST_BYTES)
                val read = connection.getInputStream().read(buffer)
                val raw = if (read > 0) String(buffer, 0, read, Charsets.UTF_8) else ""
                val callback = parseFormPostCallback(raw)

                runCatching {
                    connection.getOutputStream().apply {
                        write(callbackResponsePage(callback is FormPostCallback.Success).toByteArray())
                        flush()
                    }
                }.onFailure { failure ->
                    // The tokens matter more than the courtesy page. A browser that hung up early
                    // must not turn a completed authorization into a failed one.
                    Logger.d("Engine", failure) { "Could not write the callback page" }
                }
                callback
            }
        } catch (timeout: SocketTimeoutException) {
            Logger.i("Engine", timeout) { "No callback arrived before the deadline" }
            FormPostCallback.Malformed("timed out waiting for the portal")
        } catch (failure: Exception) {
            Logger.w("Engine", failure) { "The loopback callback failed" }
            FormPostCallback.Malformed(failure.message ?: "loopback failure")
        }
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private companion object {
        /** `127.0.0.1`, not `localhost`: RFC 8252 §8.3 — the name can resolve elsewhere. */
        const val LOOPBACK = "127.0.0.1"
        const val READ_TIMEOUT_MS = 10_000
        const val MAX_REQUEST_BYTES = 64 * 1024
    }
}
