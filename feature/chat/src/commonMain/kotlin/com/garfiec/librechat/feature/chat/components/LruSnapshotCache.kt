package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Compose-observable LRU cache. Reads via [get] participate in Compose snapshots
 * through the backing [SnapshotStateMap], so a [put] from a background thread
 * triggers recomposition of any composable that read the same key.
 *
 * [SnapshotStateMap] already routes writes through Compose's snapshot system —
 * an explicit `Snapshot.withMutableSnapshot { ... }` wrap would create one
 * mutable snapshot per writing thread and cause `SnapshotApplyConflictException`
 * under concurrent puts. The lock guards [insertionOrder] (a plain `ArrayDeque`,
 * not thread-safe) and serializes the read-modify-write pair on [map].
 */
open class LruSnapshotCache<V>(private val maxEntries: Int) {

    private val map: SnapshotStateMap<String, V> = mutableStateMapOf()
    private val insertionOrder = ArrayDeque<String>()
    private val lock = SynchronizedObject()

    operator fun get(key: String): V? = map[key]

    fun put(key: String, value: V) {
        synchronized(lock) {
            if (key !in map) {
                insertionOrder.addLast(key)
                while (insertionOrder.size > maxEntries) {
                    map.remove(insertionOrder.removeFirst())
                }
            }
            map[key] = value
        }
    }
}
