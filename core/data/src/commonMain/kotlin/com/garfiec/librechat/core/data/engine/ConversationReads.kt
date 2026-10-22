package com.garfiec.librechat.core.data.engine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * When this device last showed each conversation (16/10/2026), and so which ones hold a message
 * the person has not seen.
 *
 * [floorMillis] is when the device started keeping count: a conversation nobody opened here is
 * read up to it, so the first launch of this version does not mark a whole history unread.
 */
data class ReadSnapshot(
    val floorMillis: Long,
    val seen: Map<String, Long> = emptyMap(),
) {
    /** Whether [sessionId] moved after it was last shown here (or after the floor, never shown). */
    fun isUnread(sessionId: String, lastActivityMillis: Long?): Boolean =
        lastActivityMillis != null && lastActivityMillis > (seen[sessionId] ?: floorMillis)

    /**
     * With the scheduler's own read marks (22/10/2026): a conversation whose question the server
     * withdrew reads as read up to then, on this device too. The later of the two marks wins.
     */
    fun withServerReads(server: Map<String, Long>): ReadSnapshot =
        if (server.isEmpty()) {
            this
        } else {
            copy(seen = seen + server.filter { (session, at) -> at > (seen[session] ?: Long.MIN_VALUE) })
        }
}

/** What the lists read to tell an unread conversation, and what opening one writes. */
interface ConversationReads {
    suspend fun snapshot(): ReadSnapshot

    /** The conversation was on screen at [atMillis]: everything it said until then is read. */
    suspend fun markRead(sessionId: String, atMillis: Long)

    /** Forgets everything — part of signing out, with the other local session caches. */
    suspend fun clear()
}

/**
 * [ConversationReads] over the app's preferences: one capped JSON map and the floor, the same
 * arrangement as [EngineSessionKinds] and for the same reasons.
 */
class EngineConversationReads(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ConversationReads {

    override suspend fun snapshot(): ReadSnapshot = withContext(ioDispatcher) {
        val prefs = dataStore.data.first()
        val floor = prefs[FLOOR] ?: now().also { first -> dataStore.edit { it[FLOOR] = first } }
        ReadSnapshot(floorMillis = floor, seen = decode(prefs[SEEN]))
    }

    override suspend fun markRead(sessionId: String, atMillis: Long) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                val seen = LinkedHashMap(decode(prefs[SEEN]))
                // Remove then put: the map's order is its eviction order, the latest written last.
                val before = seen.remove(sessionId)
                seen[sessionId] = maxOf(before ?: 0L, atMillis)
                prefs[SEEN] = json.encodeToString(seen.entries.toList().takeLast(CAPACITY).associate { it.key to it.value })
                if (prefs[FLOOR] == null) prefs[FLOOR] = now()
            }
        }
    }

    override suspend fun clear() {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                prefs.remove(SEEN)
                prefs.remove(FLOOR)
            }
        }
    }

    /** An unreadable map reads as « nothing seen »: everything after the floor is unread again. */
    private fun decode(raw: String?): Map<String, Long> =
        raw?.let { runCatching { json.decodeFromString<Map<String, Long>>(it) }.getOrNull() }.orEmpty()

    private companion object {
        val SEEN = stringPreferencesKey("engine_conversations_seen")
        val FLOOR = longPreferencesKey("engine_conversations_seen_floor")

        /** As many as the session kinds: months of conversations, a few tens of kilobytes. */
        const val CAPACITY = 1_000
    }
}
