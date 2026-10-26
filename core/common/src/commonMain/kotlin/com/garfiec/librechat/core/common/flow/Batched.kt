package com.garfiec.librechat.core.common.flow

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The items [batchable] says so of, gathered for [windowMillis] after the first and emitted as one
 * list; any other item flushes what is gathered, then goes out alone, in order (26/10/2026 evening).
 * A streaming answer comes a few characters at a time, each delta a state update and a
 * recomposition of the conversation: batched, the screen redraws a dozen times a second instead
 * of a hundred, and nothing is reordered.
 */
fun <T> Flow<T>.batched(windowMillis: Long, batchable: (T) -> Boolean): Flow<List<T>> = channelFlow {
    val pending = mutableListOf<T>()
    val lock = Mutex()
    var flush: Job? = null
    suspend fun flushPending() {
        val batch = lock.withLock { if (pending.isEmpty()) null else pending.toList().also { pending.clear() } }
        if (batch != null) send(batch)
    }
    collect { item ->
        if (batchable(item)) {
            val first = lock.withLock {
                pending += item
                pending.size == 1
            }
            if (first) {
                flush = launch {
                    delay(windowMillis)
                    flushPending()
                }
            }
        } else {
            flush?.cancel()
            flushPending()
            send(listOf(item))
        }
    }
    flush?.cancel()
    flushPending()
}

/** Keeps the receiver's items flowing through a channel, for tests that drive time by hand. */
fun <T> Channel<T>.asBatchedSource(): Flow<T> = consumeAsFlow()
