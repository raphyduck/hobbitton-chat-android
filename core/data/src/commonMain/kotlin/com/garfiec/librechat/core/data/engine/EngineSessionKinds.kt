package com.garfiec.librechat.core.data.engine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * What this device knows about which engine session is a chat and which is a task (D-077).
 *
 * The first rule of [classifySession]: the app records the kind **when it creates the session**, so
 * a chat started here is never mistaken for a task, and never costs a transcript read to recognise.
 * Verdicts learned later from a session's messages are recorded too, so each foreign session is
 * read at most once.
 */
interface SessionKindStore {
    suspend fun all(): Map<String, EngineSessionKind>

    suspend fun record(sessionId: String, kind: EngineSessionKind) = recordAll(mapOf(sessionId to kind))

    suspend fun recordAll(kinds: Map<String, EngineSessionKind>)

    /** Forgets everything — part of signing out, with the rest of the local session caches. */
    suspend fun clear()
}

/**
 * [SessionKindStore] over the app's preferences: one JSON map, capped, the same arrangement as
 * `MissionReadingPositions` and for the same reasons — a key per session would grow without bound
 * on ids that disappear when the engine prunes its history.
 *
 * Device-scoped: the portal identity is the only one this app has, and signing out clears it.
 */
class EngineSessionKinds(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) : SessionKindStore {

    override suspend fun all(): Map<String, EngineSessionKind> = withContext(ioDispatcher) { stored() }

    override suspend fun recordAll(kinds: Map<String, EngineSessionKind>) {
        if (kinds.isEmpty()) return
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                val current = decode(prefs[KEY])
                val merged = cappedMerge(current, kinds, CAPACITY)
                prefs[KEY] = json.encodeToString(merged.mapValues { it.value.name })
            }
        }
    }

    override suspend fun clear() {
        withContext(ioDispatcher) { dataStore.edit { prefs -> prefs.remove(KEY) } }
    }

    private suspend fun stored(): Map<String, EngineSessionKind> = decode(dataStore.data.first()[KEY])

    /** An unreadable map reads as « nothing recorded »: every session is then classified afresh. */
    private fun decode(raw: String?): Map<String, EngineSessionKind> {
        if (raw == null) return emptyMap()
        val names = runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
        return names.mapNotNull { (id, name) ->
            EngineSessionKind.entries.firstOrNull { it.name == name }?.let { id to it }
        }.toMap()
    }

    private companion object {
        val KEY = stringPreferencesKey("engine_session_kinds")

        /** Months of chats and scheduled runs; a few tens of kilobytes at worst. */
        const val CAPACITY = 1_000
    }
}

/**
 * [current] with [added] written over it, the newest last, and the oldest dropped beyond
 * [capacity]. Insertion order is the only order a map keeps, hence the remove-then-put.
 */
internal fun cappedMerge(
    current: Map<String, EngineSessionKind>,
    added: Map<String, EngineSessionKind>,
    capacity: Int,
): Map<String, EngineSessionKind> {
    val merged = LinkedHashMap(current)
    added.forEach { (id, kind) ->
        merged.remove(id)
        merged[id] = kind
    }
    return merged.entries.toList().takeLast(capacity).associate { it.key to it.value }
}
