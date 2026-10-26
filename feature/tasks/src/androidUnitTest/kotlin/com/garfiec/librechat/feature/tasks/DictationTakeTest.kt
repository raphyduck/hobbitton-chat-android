package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.feature.tasks.components.DictationTake
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Le filet de la dictée : ce que le micro a entendu depuis un instant donné est rejoué, dans
 * l'ordre, au reconnaisseur qui repart ; ce qui est plus vieux que la fenêtre gardée est oublié.
 */
class DictationTakeTest {
    private val second = 1_000_000_000L

    @Test
    fun `rejoue dans l'ordre ce qui a été entendu depuis l'instant demandé`() {
        val take = DictationTake(keepNanos = 30 * second)
        take.add(1 * second, byteArrayOf(1))
        take.add(2 * second, byteArrayOf(2))
        take.add(3 * second, byteArrayOf(3))

        assertThat(take.since(2 * second).map { it.single() }).containsExactly(2.toByte(), 3.toByte()).inOrder()
        assertThat(take.since(4 * second)).isEmpty()
    }

    @Test
    fun `oublie ce qui est plus vieux que la fenêtre gardée`() {
        val take = DictationTake(keepNanos = 5 * second)
        take.add(1 * second, byteArrayOf(1))
        take.add(2 * second, byteArrayOf(2))
        take.add(8 * second, byteArrayOf(8))

        assertThat(take.since(0).map { it.single() }).containsExactly(8.toByte())
    }

    @Test
    fun `garde toujours le dernier morceau`() {
        val take = DictationTake(keepNanos = 1)
        take.add(10 * second, byteArrayOf(7))

        assertThat(take.since(0)).hasSize(1)
    }
}
