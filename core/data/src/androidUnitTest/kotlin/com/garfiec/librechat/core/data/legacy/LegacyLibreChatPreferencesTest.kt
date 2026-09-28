package com.garfiec.librechat.core.data.legacy

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.garfiec.librechat.core.data.datastore.GlobalProfileStore
import com.garfiec.librechat.core.model.chat.GlobalProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** What the start-up cleanup takes from the preferences, and what it leaves (D-077). */
class LegacyLibreChatPreferencesTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dataStore by lazy {
        PreferenceDataStoreFactory.create { File(tmpFolder.root, "settings.preferences_pb") }
    }

    private fun key(name: String) = stringPreferencesKey(name)

    @Test
    fun `LibreChat's entries go, the engine's and the display's stay`() = runTest {
        dataStore.edit { prefs ->
            prefs[key("server_url")] = "https://chat.example.com"
            prefs[key("account_roster")] = "[]"
            prefs[key("acct:a1:last_used_model")] = "gpt"
            prefs[key("srv:s1:cached_startup_config")] = "{}"
            prefs[booleanPreferencesKey("prefetch_enabled")] = true
            prefs[key("engine_base_url")] = "https://agent.example.com"
            prefs[key("engine_session_kinds")] = "{}"
            prefs[key("theme_mode")] = "dark"
            prefs[key("selected_language")] = "fr"
            prefs[key("chat_font_size")] = "large"
        }

        dataStore.edit { it.purgeLibreChatEntries() }

        val left = dataStore.data.first().asMap().keys.map { it.name }.toSet()
        assertThat(left).containsExactly(
            "engine_base_url",
            "engine_session_kinds",
            "theme_mode",
            "selected_language",
            "chat_font_size",
        )
    }

    @Test
    fun `the signed-in account's profile becomes the device's`() = runTest {
        dataStore.edit { prefs ->
            prefs[key("active_account_id")] = "a1"
            prefs[key("acct:a1:chat_profile_instructions")] = "Réponds en français."
            prefs[key("acct:a1:chat_profile_enabled")] = "true"
            prefs[key("acct:a2:chat_profile_instructions")] = "Autre compte."
        }

        dataStore.edit { prefs ->
            prefs.adoptAccountProfile()
            prefs.purgeLibreChatEntries()
        }

        assertThat(GlobalProfileStore(dataStore).current())
            .isEqualTo(GlobalProfile(enabled = true, instructions = "Réponds en français."))
    }

    @Test
    fun `a profile the device already has is kept`() = runTest {
        val store = GlobalProfileStore(dataStore)
        store.save(GlobalProfile(instructions = "Celui de l'appareil."))
        dataStore.edit { prefs ->
            prefs[key("active_account_id")] = "a1"
            prefs[key("acct:a1:chat_profile_instructions")] = "Celui du compte."
        }

        dataStore.edit { it.adoptAccountProfile() }

        assertThat(store.current().instructions).isEqualTo("Celui de l'appareil.")
    }

    @Test
    fun `without a signed-in account, the only account with a profile is taken`() = runTest {
        dataStore.edit { prefs ->
            prefs[key("acct:a2:chat_profile_instructions")] = "Seul compte."
        }

        dataStore.edit { it.adoptAccountProfile() }

        assertThat(GlobalProfileStore(dataStore).current().instructions).isEqualTo("Seul compte.")
    }
}
