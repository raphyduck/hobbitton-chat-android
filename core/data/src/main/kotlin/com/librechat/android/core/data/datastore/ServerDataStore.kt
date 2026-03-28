package com.librechat.android.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.librechat.android.core.network.client.ServerUrlProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class ServerDataStore(
    private val dataStore: DataStore<Preferences>,
) : ServerUrlProvider {

    private val _currentUrl = MutableStateFlow("")
    val currentUrlFlow: Flow<String> = _currentUrl

    init {
        // Load the persisted URL synchronously so getBaseUrl() is ready
        // immediately after construction. This is a singleton created once
        // at app startup, so a brief block on Dispatchers.IO is acceptable.
        _currentUrl.value = runBlocking(Dispatchers.IO) {
            dataStore.data
                .map { prefs -> prefs[KEY_SERVER_URL].orEmpty() }
                .first()
        }
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
    }

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
    }
}
