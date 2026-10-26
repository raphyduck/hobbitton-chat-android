package com.garfiec.librechat.core.data.engine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.garfiec.librechat.core.model.engine.EngineModelRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The model this device last sent each conversation with (26/10/2026).
 *
 * The engine keeps no model per session: the chip and the next message followed the model the
 * session's LAST assistant turn ran on, whoever wrote it. A reminder the scheduler plays back into
 * a conversation, a relance posing its decision in its own, ran on another model, and the person's
 * next message followed it (Raphaël: « le choix du modèle change en cours de route »). What the
 * person chose for a conversation, or what Auto chose for it, is theirs until they change it.
 */
interface ConversationModelStore {
    suspend fun modelOf(sessionId: String): EngineModelRef?

    suspend fun record(sessionId: String, model: EngineModelRef)

    /** Forgets everything — part of signing out, with the other local session caches. */
    suspend fun clear()
}

/**
 * [ConversationModelStore] over the app's preferences: one capped JSON map, the same arrangement
 * as [EngineSessionKinds] and for the same reasons.
 */
class EngineConversationModels(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
) : ConversationModelStore {

    override suspend fun modelOf(sessionId: String): EngineModelRef? =
        withContext(ioDispatcher) { decode(dataStore.data.first()[KEY])[sessionId] }

    override suspend fun record(sessionId: String, model: EngineModelRef) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                val models = LinkedHashMap(decode(prefs[KEY]))
                // Remove then put: the map's order is its eviction order, the latest written last.
                models.remove(sessionId)
                models[sessionId] = model
                prefs[KEY] = json.encodeToString(models.entries.toList().takeLast(CAPACITY).associate { it.key to it.value })
            }
        }
    }

    override suspend fun clear() {
        withContext(ioDispatcher) { dataStore.edit { prefs -> prefs.remove(KEY) } }
    }

    /** An unreadable map reads as « nothing remembered »: the last turn's model stands again. */
    private fun decode(raw: String?): Map<String, EngineModelRef> =
        raw?.let { runCatching { json.decodeFromString<Map<String, EngineModelRef>>(it) }.getOrNull() }.orEmpty()

    private companion object {
        val KEY = stringPreferencesKey("engine_conversation_models")

        /** As many as the session kinds: months of conversations, a few tens of kilobytes. */
        const val CAPACITY = 1_000
    }
}
