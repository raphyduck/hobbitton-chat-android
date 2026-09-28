package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.garfiec.librechat.core.model.chat.GlobalProfile
import kotlinx.coroutines.flow.first

/**
 * The profile as one value, for whoever is about to put words in front of a model.
 *
 * A seam rather than the store itself: what a send path needs is « what does the profile say right
 * now », not a DataStore. It keeps [com.garfiec.librechat.core.data.engine.EngineMissionRepository]
 * testable without a filesystem, and says in its own signature that the read is a snapshot and not
 * a subscription.
 */
fun interface GlobalProfileSource {
    suspend fun current(): GlobalProfile
}

/**
 * The profile as its editor needs it: read once, written back whole. The engine shell's
 * instructions screen (D-077) holds this and not the store, so it can be tested without a
 * filesystem and cannot reach the store's account plumbing.
 */
interface GlobalProfileEditor : GlobalProfileSource {
    suspend fun save(profile: GlobalProfile)
}

/**
 * Where the global profile is kept: the device's preferences, under `device:` keys.
 *
 * It used to follow the signed-in LibreChat account (`acct:<id>:` keys). LibreChat is gone
 * (D-077); an install that had an account has that account's profile moved under the device keys
 * once, by the start-up cleanup of LibreChat's data (`LegacyLibreChatCleanup`), so nothing
 * already written is lost.
 */
class GlobalProfileStore(
    private val dataStore: DataStore<Preferences>,
) : GlobalProfileEditor {

    /**
     * The profile as it stands, for the send path.
     *
     * Reads rather than collects: a chat request needs one value now, and a `Flow` there would mean
     * a subscription per message.
     */
    override suspend fun current(): GlobalProfile = dataStore.data.first().readProfile()

    override suspend fun save(profile: GlobalProfile) {
        dataStore.edit { prefs ->
            prefs[deviceKey(ENABLED)] = profile.enabled.toString()
            prefs[deviceKey(INSTRUCTIONS)] = profile.instructions
            // Joined on a newline rather than a comma: a server name may not contain one, and this
            // survives a name with a comma in it.
            prefs[deviceKey(MCP_SERVERS)] = profile.mcpServers.joinToString("\n")
        }
    }

    private fun Preferences.readProfile() = GlobalProfile(
        // Absent means ON. A profile someone filled in and never switched on would be the most
        // confusing possible default — it would look configured and do nothing.
        enabled = this[deviceKey(ENABLED)]?.toBooleanStrictOrNull() ?: true,
        instructions = this[deviceKey(INSTRUCTIONS)].orEmpty(),
        mcpServers = this[deviceKey(MCP_SERVERS)]
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty(),
    )

    internal companion object {
        const val ENABLED = "chat_profile_enabled"
        const val INSTRUCTIONS = "chat_profile_instructions"
        const val MCP_SERVERS = "chat_profile_mcp_servers"
        private const val DEVICE_PREFIX = "device:"

        /** The three settings, in the order they are written. */
        val BASES = listOf(ENABLED, INSTRUCTIONS, MCP_SERVERS)

        fun deviceKey(base: String): Preferences.Key<String> = stringPreferencesKey("$DEVICE_PREFIX$base")
    }
}
