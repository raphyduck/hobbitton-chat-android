package com.garfiec.librechat.core.common.flow

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BatchedTest {

    @Test
    fun `deltas within the window come out as one list, in order`() = runTest {
        val source = Channel<String>(Channel.UNLIMITED)
        val out = mutableListOf<List<String>>()
        val reader = launch { source.asBatchedSource().batched(80) { it.startsWith("d") }.toList(out) }
        source.send("d1")
        source.send("d2")
        advanceTimeBy(100)
        source.send("d3")
        source.close()
        reader.join()
        assertEquals(listOf(listOf("d1", "d2"), listOf("d3")), out)
    }

    @Test
    fun `another item flushes the pending deltas first and goes out alone`() = runTest {
        val source = Channel<String>(Channel.UNLIMITED)
        val out = mutableListOf<List<String>>()
        val reader = launch { source.asBatchedSource().batched(80) { it.startsWith("d") }.toList(out) }
        source.send("d1")
        source.send("part")
        source.send("d2")
        source.send("idle")
        source.close()
        reader.join()
        assertEquals(listOf(listOf("d1"), listOf("part"), listOf("d2"), listOf("idle")), out)
    }
}
