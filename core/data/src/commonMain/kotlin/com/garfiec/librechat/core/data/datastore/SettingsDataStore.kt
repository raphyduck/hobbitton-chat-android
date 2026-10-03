package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * The display preferences the app still reads (the language applied at the root and the size of
 * the conversation's text, both set from LibreChat's settings screens and read as stored) and the
 * one it writes: whether a question or a finished reply rings ([attentionSound]).
 */
class SettingsDataStore(
    private val dataStore: DataStore<Preferences>,
    appScope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
) {
    /**
     * Selected app language as a BCP-47/ISO code (e.g. "es", "zh"), or [DEFAULT_LANGUAGE] (the
     * "follow the device locale" sentinel) when the user hasn't chosen one. Drives the runtime
     * locale applied at the app root.
     */
    val selectedLanguage: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_LANGUAGE] ?: DEFAULT_LANGUAGE
    }

    /**
     * Initial language seeded for the first compose frame, warmed up asynchronously off the Main
     * thread (Koin instantiates this singleton on Main at startup, so the read must not block).
     * Stays [DEFAULT_LANGUAGE] until [isReady] flips; the root composable gates on [isReady] so the
     * persisted locale is applied before the first frame draws (no flash of the wrong language).
     */
    @Volatile
    var initialSelectedLanguage: String = DEFAULT_LANGUAGE
        private set

    private val _isReady = MutableStateFlow(false)

    /** Flips true once the async warm-up has resolved [initialSelectedLanguage]. */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        appScope.launch(ioDispatcher) {
            try {
                val prefs = dataStore.data.first()
                initialSelectedLanguage = prefs[KEY_SELECTED_LANGUAGE] ?: DEFAULT_LANGUAGE
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the default; the live flow still drives the UI value.
            } finally {
                _isReady.value = true
            }
        }
    }

    val chatFontSize: Flow<ChatFontSize> = dataStore.data.map { prefs ->
        ChatFontSize.fromString(prefs[KEY_CHAT_FONT_SIZE])
    }

    /**
     * Sound and notifications when a conversation needs the person: a question waiting for an
     * answer, a reply ready while they were elsewhere (03/10/2026). On unless switched off in
     * Settings: a question nobody hears about blocks its turn until somebody happens to look.
     */
    val attentionSound: Flow<Boolean> = dataStore.data.map { prefs -> prefs[KEY_ATTENTION_SOUND] ?: true }

    suspend fun setAttentionSound(enabled: Boolean) {
        dataStore.edit { it[KEY_ATTENTION_SOUND] = enabled }
    }

    companion object {
        /**
         * Sentinel meaning "follow the device locale" — the default until the user picks a
         * specific language. Maps to a null locale override at the app root, so formatting and
         * resource resolution keep following the system.
         */
        const val DEFAULT_LANGUAGE = "system"

        private val KEY_SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        private val KEY_CHAT_FONT_SIZE = stringPreferencesKey("chat_font_size")
        private val KEY_ATTENTION_SOUND = booleanPreferencesKey("attention_sound")
    }
}
