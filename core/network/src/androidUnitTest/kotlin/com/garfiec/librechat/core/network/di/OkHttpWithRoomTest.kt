package com.garfiec.librechat.core.network.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * More than five requests reach one host at once (25/10/2026): two event feeds held two of
 * OkHttp's five default places for good, and a conversation's transcript queued behind the rest.
 */
class OkHttpWithRoomTest {

    @Test
    fun eightRequestsAreInFlightToOneHostAtOnce() = runBlocking {
        val held = 8
        val arrived = CountDownLatch(held)
        val server = ServerSocket(0)
        // Each connection is answered only once all eight have arrived: with five places, the
        // last three never leave the client's queue and the latch never opens.
        val acceptor = thread(isDaemon = true) {
            repeat(held) {
                val socket = runCatching { server.accept() }.getOrNull() ?: return@thread
                thread(isDaemon = true) {
                    socket.use { s ->
                        val reader = s.getInputStream().bufferedReader()
                        while (reader.readLine().orEmpty().isNotEmpty()) Unit
                        arrived.countDown()
                        arrived.await(20, TimeUnit.SECONDS)
                        s.getOutputStream().write(
                            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray(),
                        )
                    }
                }
            }
        }
        val client = HttpClient(OkHttpWithRoom)
        try {
            val bodies = withTimeout(15_000) {
                (1..held).map { i -> async { client.get("http://127.0.0.1:${server.localPort}/r$i").bodyAsText() } }
                    .awaitAll()
            }
            assertEquals(List(held) { "ok" }, bodies)
            assertTrue(arrived.await(0, TimeUnit.SECONDS))
        } finally {
            client.close()
            server.close()
            acceptor.interrupt()
        }
    }

    /**
     * A request on a connection that never answers is freed by [OkHttpConnections.dropAll] at once,
     * not by a timeout (26/10/2026: the dead connection left by a sleep held every retry).
     */
    @Test
    fun droppingTheConnectionsFailsAHungRequestAtOnce() = runBlocking {
        val server = ServerSocket(0)
        val mute = thread(isDaemon = true) {
            runCatching { server.accept() }.getOrNull()?.let { socket ->
                runCatching { Thread.sleep(60_000) }
                socket.close()
            }
        }
        val client = HttpClient(OkHttpWithRoom) { install(HttpTimeout) { socketTimeoutMillis = 60_000 } }
        try {
            val started = System.nanoTime()
            val request = async { runCatching { client.get("http://127.0.0.1:${server.localPort}/hung").bodyAsText() } }
            delay(500)
            OkHttpConnections.dropAll()
            val outcome = withTimeout(5_000) { request.await() }
            assertTrue(outcome.isFailure)
            assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000)
        } finally {
            client.close()
            server.close()
            mute.interrupt()
        }
    }
}
