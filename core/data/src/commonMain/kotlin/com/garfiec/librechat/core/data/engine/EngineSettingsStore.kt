package com.garfiec.librechat.core.data.engine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.garfiec.librechat.core.common.network.CleartextPolicy
import com.garfiec.librechat.core.network.engine.EngineAccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.concurrent.Volatile

/**
 * Where the engine, the portal and the scheduler are, as the person configured them.
 *
 * **Not derived from the chat's server URL.** `chat.hobbitton.at` → `agent.hobbitton.at` is a
 * tempting rule and a trap: it is true of one deployment, invisible when it stops being true, and
 * it silently sends the portal's bearer to whatever host that transformation lands on. An explicit
 * setting is one screen more and no guessing. See D-034 (server-side decision log).
 *
 * **Addresses only, since D-076.** The engine's user name and password are gone — the edge
 * presents the engine's Basic itself — and so is the client id, which is a constant
 * (`PORTAL_CLIENT_ID`). Nothing stored here is a secret any more.
 */
class EngineSettingsStore(
    private val dataStore: DataStore<Preferences>,
) {

    val baseUrl: Flow<String> = dataStore.data.map { it[KEY_BASE_URL].orEmpty() }
    val issuerUrl: Flow<String> = dataStore.data.map { it[KEY_ISSUER_URL].orEmpty() }

    /**
     * Where the scheduler lives, e.g. `https://sched.hobbitton.at`. Blank is a normal state: the
     * engine works without it, and the Tasks tab simply shows no recurring missions.
     *
     * Not derived from the engine's URL, for the same reason the engine's is not derived from the
     * chat's: `agent.` → `sched.` is true of one deployment and invisible when it stops being.
     */
    val schedulerUrl: Flow<String> = dataStore.data.map { it[KEY_SCHEDULER_URL].orEmpty() }

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
            schedulerUrl = prefs[KEY_SCHEDULER_URL].orEmpty().trimEnd('/'),
        )
        return candidate.takeIf { it.isConfigured }.also { cached = it }
    }

    /**
     * Persists the addresses.
     *
     * Refuses an `http://` address whose host is not private (finding F5, 26/09/2026): the portal's
     * bearer goes on every request to the engine and the scheduler, and the `redirect_uri` built
     * from the scheduler's address receives the authorization code. The form checks the same rule
     * first and names the field; this is the last line, for any caller that is not the form.
     * [CleartextPolicy] is the one definition of « private ».
     */
    suspend fun save(
        baseUrl: String,
        issuerUrl: String,
        schedulerUrl: String = "",
    ) {
        require(CleartextPolicy.isPermitted(baseUrl)) { "The engine address may use http:// only towards a private host" }
        require(CleartextPolicy.isPermitted(issuerUrl)) { "The portal address may use http:// only towards a private host" }
        require(CleartextPolicy.isPermitted(schedulerUrl)) {
            "The scheduler address may use http:// only towards a private host"
        }
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = baseUrl.trim().trimEnd('/')
            prefs[KEY_ISSUER_URL] = issuerUrl.trim().trimEnd('/')
            prefs[KEY_SCHEDULER_URL] = schedulerUrl.trim().trimEnd('/')
            prefs.dropRetiredKeys()
        }
    }

    suspend fun forget() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_BASE_URL)
            prefs.remove(KEY_ISSUER_URL)
            prefs.remove(KEY_SCHEDULER_URL)
            prefs.dropRetiredKeys()
        }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("engine_base_url")
        val KEY_ISSUER_URL = stringPreferencesKey("engine_issuer_url")
        val KEY_SCHEDULER_URL = stringPreferencesKey("scheduler_base_url")

        /**
         * Written by builds before D-076: the engine's user name and the editable client id. Not
         * secrets — the password lived in the encrypted store, which purges its own copy — but
         * dead values that nothing reads, dropped on the next write rather than left forever.
         */
        val RETIRED_KEYS = listOf(
            stringPreferencesKey("engine_username"),
            stringPreferencesKey("engine_client_id"),
        )
    }

    private fun MutablePreferences.dropRetiredKeys() {
        RETIRED_KEYS.forEach { remove(it) }
    }
}
