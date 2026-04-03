package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.StartupConfig
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import co.touchlab.kermit.Logger

class ConfigCacheDataStore(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {

    suspend fun saveStartupConfig(config: StartupConfig) {
        try {
            val serialized = json.encodeToString(StartupConfig.serializer(), config)
            dataStore.edit { prefs -> prefs[KEY_STARTUP_CONFIG] = serialized }
        } catch (e: Exception) {
            Logger.w(e) { "Failed to cache startup config" }
        }
    }

    suspend fun loadStartupConfig(): StartupConfig? {
        return try {
            val prefs = dataStore.data.first()
            val serialized = prefs[KEY_STARTUP_CONFIG] ?: return null
            json.decodeFromString(StartupConfig.serializer(), serialized)
        } catch (e: Exception) {
            Logger.w(e) { "Failed to load cached startup config" }
            null
        }
    }

    suspend fun saveEndpointConfigs(endpoints: Map<String, EndpointConfig>) {
        try {
            val serializer = MapSerializer(String.serializer(), EndpointConfig.serializer())
            val serialized = json.encodeToString(serializer, endpoints)
            dataStore.edit { prefs -> prefs[KEY_ENDPOINT_CONFIGS] = serialized }
        } catch (e: Exception) {
            Logger.w(e) { "Failed to cache endpoint configs" }
        }
    }

    suspend fun loadEndpointConfigs(): Map<String, EndpointConfig>? {
        return try {
            val prefs = dataStore.data.first()
            val serialized = prefs[KEY_ENDPOINT_CONFIGS] ?: return null
            val serializer = MapSerializer(String.serializer(), EndpointConfig.serializer())
            json.decodeFromString(serializer, serialized)
        } catch (e: Exception) {
            Logger.w(e) { "Failed to load cached endpoint configs" }
            null
        }
    }

    suspend fun saveAvailableModels(models: Map<String, List<String>>) {
        try {
            val serializer = MapSerializer(
                String.serializer(),
                ListSerializer(String.serializer()),
            )
            val serialized = json.encodeToString(serializer, models)
            dataStore.edit { prefs -> prefs[KEY_AVAILABLE_MODELS] = serialized }
        } catch (e: Exception) {
            Logger.w(e) { "Failed to cache available models" }
        }
    }

    suspend fun loadAvailableModels(): Map<String, List<String>>? {
        return try {
            val prefs = dataStore.data.first()
            val serialized = prefs[KEY_AVAILABLE_MODELS] ?: return null
            val serializer = MapSerializer(
                String.serializer(),
                ListSerializer(String.serializer()),
            )
            json.decodeFromString(serializer, serialized)
        } catch (e: Exception) {
            Logger.w(e) { "Failed to load cached available models" }
            null
        }
    }

    companion object {
        private val KEY_STARTUP_CONFIG = stringPreferencesKey("cached_startup_config")
        private val KEY_ENDPOINT_CONFIGS = stringPreferencesKey("cached_endpoint_configs")
        private val KEY_AVAILABLE_MODELS = stringPreferencesKey("cached_available_models")
    }
}
