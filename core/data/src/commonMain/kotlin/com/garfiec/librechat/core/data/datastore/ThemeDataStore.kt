package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class ThemeDataStore(
    private val dataStore: DataStore<Preferences>,
) {

    /**
     * Synchronously loaded initial value so the first compose frame uses the correct theme
     * and avoids a flash of light mode when the user has dark mode saved.
     */
    val initialThemeMode: ThemeMode = runBlocking(Dispatchers.IO) {
        dataStore.data.map { prefs -> prefs[KEY_THEME].toThemeMode() }.first()
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[KEY_THEME].toThemeMode()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME] = when (mode) {
                ThemeMode.SYSTEM -> "system"
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
            }
        }
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_mode")

        private fun String?.toThemeMode(): ThemeMode = when (this) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }
}
