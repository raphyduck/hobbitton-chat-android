package com.garfiec.librechat.feature.tasks.components

/**
 * What the mic heard lately, in order, each chunk stamped with the clock it was read at: the
 * dictation's safety net (26/10/2026). The phone's recognizer reads the mic through a pipe and may
 * end its session at a pause; the words spoken while it is being started again would be lost, so
 * the next session is first fed everything heard [since] the recognizer stopped taking words in.
 * Keeps the last [keepNanos] of audio and no more; thread-safe, the mic adds from its own thread.
 */
internal class DictationTake(private val keepNanos: Long) {
    private val chunks = ArrayDeque<Pair<Long, ByteArray>>()

    @Synchronized
    fun add(atNanos: Long, bytes: ByteArray) {
        chunks.addLast(atNanos to bytes)
        while (chunks.size > 1 && chunks.first().first < atNanos - keepNanos) chunks.removeFirst()
    }

    /** The chunks read at [atNanos] or later, oldest first. */
    @Synchronized
    fun since(atNanos: Long): List<ByteArray> = chunks.filter { it.first >= atNanos }.map { it.second }

    @Synchronized
    fun clear() = chunks.clear()
}
