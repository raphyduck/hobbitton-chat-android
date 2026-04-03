package com.librechat.android.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.librechat.android.core.network.client.ServerUrlProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Optional fallback for reading/writing the server URL in a location
 * that survives app reinstall (e.g. iOS Keychain). On platforms without
 * a fallback (Android), pass null.
 */
interface ServerUrlKeychainFallback {
    fun readServerUrl(): String?
    fun writeServerUrl(url: String)
    fun removeServerUrl()
}

class ServerDataStore(
    private val dataStore: DataStore<Preferences>,
    ioDispatcher: CoroutineDispatcher,
    private val keychainFallback: ServerUrlKeychainFallback? = null,
) : ServerUrlProvider {

    private val _currentUrl = MutableStateFlow("")
    val currentUrlFlow: Flow<String> = _currentUrl

    init {
        // Load the persisted URL synchronously so getBaseUrl() is ready
        // immediately after construction. This is a singleton created once
        // at app startup, so a brief block is acceptable.
        var url = runBlocking(ioDispatcher) {
            dataStore.data
                .map { prefs -> prefs[KEY_SERVER_URL].orEmpty() }
                .first()
        }
        // If DataStore is empty (e.g. after reinstall), try the Keychain fallback.
        // Keychain items persist across iOS app reinstalls.
        if (url.isBlank() && keychainFallback != null) {
            val restored = keychainFallback.readServerUrl()
            if (!restored.isNullOrBlank()) {
                url = restored
                // Re-persist to DataStore so subsequent reads don't need the fallback
                runBlocking(ioDispatcher) {
                    dataStore.edit { prefs -> prefs[KEY_SERVER_URL] = restored }
                }
            }
        }
        _currentUrl.value = url
    }

    override fun getBaseUrl(): String = _currentUrl.value

    fun hasServerUrl(): Flow<Boolean> =
        dataStore.data.map { prefs ->
            !prefs[KEY_SERVER_URL].isNullOrBlank()
        }

    suspend fun setServerUrl(url: String) {
        val trimmed = url.trimEnd('/')
        _currentUrl.value = trimmed
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = trimmed
        }
        // Mirror to Keychain so the URL survives app reinstall on iOS
        keychainFallback?.writeServerUrl(trimmed)
    }

    /**
     * Clear the server URL from both DataStore and Keychain fallback.
     */
    suspend fun clearServerUrl() {
        _currentUrl.value = ""
        dataStore.edit { prefs -> prefs.remove(KEY_SERVER_URL) }
        keychainFallback?.removeServerUrl()
    }

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
    }
}
