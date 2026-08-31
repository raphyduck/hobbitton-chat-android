package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where the reader had got to in one mission's transcript. */
@Serializable
data class MissionReadingPosition(val index: Int, val offset: Int)

/**
 * Where each mission's transcript was left, so re-opening one lands where it was closed.
 *
 * A mission's conversation is long — nine tool calls and a paragraph of thinking per turn — and the
 * screen used to open at the tail every time. Someone reading back through last night's run lost
 * their place the moment they stepped out to check something (demandé le 31/08/2026).
 *
 * **One preference, not one per session.** A key per session id would grow without bound, on ids
 * that stop existing when the engine's history is pruned; a single JSON map with a cap collects its
 * own garbage. Beyond [CAPACITY] the least recently written entry goes: a position is only useful
 * while the session is still fresh in someone's mind, and the ones worth keeping are the ones just
 * visited.
 *
 * Positions are **device-scoped, not account-scoped** — like the server headers, and for a plainer
 * reason: they describe how far someone has read on this screen, not data belonging to an account.
 */
class MissionReadingPositions(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /** Null when this mission has never been left mid-transcript — the caller then opens at the tail. */
    suspend fun positionOf(sessionId: String): MissionReadingPosition? = withContext(ioDispatcher) {
        stored()[sessionId]
    }

    /**
     * Records where the reader is now. Called as they scroll, so it is deliberately cheap and
     * deliberately last-write-wins: losing one write to a race costs a few pixels of accuracy.
     */
    suspend fun remember(sessionId: String, position: MissionReadingPosition) {
        withContext(ioDispatcher) {
            val kept = (stored() - sessionId).entries
                .toList()
                .takeLast(CAPACITY - 1)
                .associate { it.key to it.value }
            // Appended last: `takeLast` is what makes this an eviction of the OLDEST, and insertion
            // order is the only ordering a LinkedHashMap gives back.
            write(kept + (sessionId to position))
        }
    }

    private suspend fun stored(): Map<String, MissionReadingPosition> {
        val raw = dataStore.data.first()[KEY] ?: return emptyMap()
        // A shape this app wrote and can no longer read is a bug that must not take the screen down
        // with it: an unreadable map means « nobody has a saved position », which is the state the
        // tab shipped with anyway.
        return runCatching { json.decodeFromString<Map<String, MissionReadingPosition>>(raw) }
            .getOrDefault(emptyMap())
    }

    private suspend fun write(positions: Map<String, MissionReadingPosition>) {
        dataStore.edit { prefs -> prefs[KEY] = json.encodeToString(positions) }
    }

    private companion object {
        val KEY = stringPreferencesKey("mission_reading_positions")

        /** Enough for a month of nightly missions; small enough that the blob stays a few hundred bytes. */
        const val CAPACITY = 50
    }
}
