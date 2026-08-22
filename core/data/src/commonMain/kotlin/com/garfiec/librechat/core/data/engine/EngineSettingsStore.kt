package com.garfiec.librechat.core.data.engine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.garfiec.librechat.core.network.engine.EngineAccess
import com.garfiec.librechat.core.network.engine.EnginePasswordStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.concurrent.Volatile

/**
 * Where the engine is and who the app claims to be, as the person configured it.
 *
 * **Not derived from the chat's server URL.** `chat.hobbitton.at` → `agent.hobbitton.at` is a
 * tempting rule and a trap: it is true of one deployment, invisible when it stops being true, and
 * it silently sends the engine's credentials to whatever host that transformation lands on. An
 * explicit setting is one screen more and no guessing. See D-034 (server-side decision log).
 *
 * The password is the one field that does **not** live here: preferences are plain text on disk,
 * and this one goes to the encrypted store instead ([EnginePasswordStore]).
 */
class EngineSettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val passwords: EnginePasswordStore,
) {

    val baseUrl: Flow<String> = dataStore.data.map { it[KEY_BASE_URL].orEmpty() }
    val issuerUrl: Flow<String> = dataStore.data.map { it[KEY_ISSUER_URL].orEmpty() }
    val clientId: Flow<String> = dataStore.data.map { it[KEY_CLIENT_ID] ?: DEFAULT_CLIENT_ID }
    val username: Flow<String> = dataStore.data.map { it[KEY_USERNAME] ?: DEFAULT_USERNAME }

    /**
     * The last value [access] produced, readable without suspending.
     *
     * Ktor's `defaultRequest` block is not a coroutine, so the base URL has to be available
     * synchronously — the same reason `ServerUrlProvider` exposes a plain getter. Null until the
     * first read; the graph warms it at startup, and every suspend [access] refreshes it.
     */
    fun cachedAccess(): EngineAccess? = cached

    @Volatile
    private var cached: EngineAccess? = null

    /**
     * Everything the network layer needs, or null when the engine has not been configured.
     *
     * Null rather than a half-filled object on purpose: a client built on a blank base URL produces
     * « unknown host » errors that read like a network outage, on a phone whose network is fine.
     */
    suspend fun access(): EngineAccess? {
        val prefs = dataStore.data.first()
        val candidate = EngineAccess(
            baseUrl = prefs[KEY_BASE_URL].orEmpty().trimEnd('/'),
            issuerUrl = prefs[KEY_ISSUER_URL].orEmpty().trimEnd('/'),
            clientId = prefs[KEY_CLIENT_ID] ?: DEFAULT_CLIENT_ID,
            username = prefs[KEY_USERNAME] ?: DEFAULT_USERNAME,
            password = passwords.read().orEmpty(),
        )
        return candidate.takeIf { it.isConfigured }.also { cached = it }
    }

    suspend fun save(
        baseUrl: String,
        issuerUrl: String,
        clientId: String = DEFAULT_CLIENT_ID,
        username: String = DEFAULT_USERNAME,
        password: String?,
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = baseUrl.trim().trimEnd('/')
            prefs[KEY_ISSUER_URL] = issuerUrl.trim().trimEnd('/')
            prefs[KEY_CLIENT_ID] = clientId.trim()
            prefs[KEY_USERNAME] = username.trim()
        }
        // Null means « leave it as it is » — the settings screen shows the password masked and does
        // not read it back, so submitting the form untouched must not wipe it.
        if (password != null) passwords.write(password)
    }

    /**
     * Whether a password is already stored — **without handing it back**.
     *
     * The settings form needs to know this to tell « leave the saved one alone » from « none has
     * ever been set », and those two need different behaviour on an empty field. Reading the
     * password itself to answer would put a secret on screen and in the view model's state for no
     * reason; a boolean answers the question.
     */
    suspend fun hasPassword(): Boolean = !passwords.read().isNullOrBlank()

    suspend fun forget() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_BASE_URL)
            prefs.remove(KEY_ISSUER_URL)
            prefs.remove(KEY_CLIENT_ID)
            prefs.remove(KEY_USERNAME)
        }
        passwords.clear()
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("engine_base_url")
        val KEY_ISSUER_URL = stringPreferencesKey("engine_issuer_url")
        val KEY_CLIENT_ID = stringPreferencesKey("engine_client_id")
        val KEY_USERNAME = stringPreferencesKey("engine_username")

        /** What Authelia is configured with server-side; overridable for another deployment. */
        const val DEFAULT_CLIENT_ID = "hobbitton-chat-android"
        const val DEFAULT_USERNAME = "opencode"
    }
}
