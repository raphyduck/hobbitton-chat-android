package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.model.chat.GlobalProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

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
 * Where the global profile is kept.
 *
 * **Account-scoped** when a LibreChat account is signed in, like the last used model and for the
 * same reason: the MCP servers of one server are not those of another, and instructions written
 * for a work account have no business riding on a personal one. The `acct:` prefix also means the
 * logout purge sweeps them with everything else, without this file having to remember to.
 *
 * **Device-scoped** otherwise — which since D-077 is every fresh Android install: LibreChat is
 * gone, nobody signs in to it, and the account resolves to none. Keying on that absent account
 * made the profile unwritable (a save returned without writing) and unreadable (the send path got
 * [GlobalProfile.NONE]), so the instructions the engine sends as `system` could never be set. An
 * install that did have a LibreChat account keeps reading and writing that account's profile, so
 * nothing already written is lost.
 */
class GlobalProfileStore(
    private val dataStore: DataStore<Preferences>,
    private val activeAccountProvider: ActiveAccountProvider,
) : GlobalProfileEditor {

    /**
     * The profile of the signed-in account, or of the device when there is none. Emits nothing
     * while the identity is still resolving — a default emitted during that window would read as
     * « no profile » and send a first message without one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val profile: Flow<GlobalProfile> = activeAccountProvider.state.flatMapLatest { state ->
        when (state) {
            AccountState.Warming -> emptyFlow()
            is AccountState.Resolved -> dataStore.data.map { prefs -> prefs.readProfile(state.id?.value) }
        }
    }

    /**
     * The profile as it stands, for the send path.
     *
     * Reads rather than collects: a chat request needs one value now, and a `Flow` there would mean
     * a subscription per message.
     */
    override suspend fun current(): GlobalProfile =
        dataStore.data.first().readProfile(activeAccountProvider.currentAccountId()?.value)

    override suspend fun save(profile: GlobalProfile) {
        val accountId = activeAccountProvider.currentAccountId()?.value
        dataStore.edit { prefs ->
            prefs[key(accountId, ENABLED)] = profile.enabled.toString()
            prefs[key(accountId, INSTRUCTIONS)] = profile.instructions
            // Joined on a newline rather than a comma: a server name may not contain one, and this
            // survives a name with a comma in it — which `GET /api/mcp/servers` is free to return.
            prefs[key(accountId, MCP_SERVERS)] = profile.mcpServers.joinToString("\n")
        }
    }

    private fun Preferences.readProfile(accountId: String?) = GlobalProfile(
        // Absent means ON. A profile someone filled in and never switched on would be the most
        // confusing possible default — it would look configured and do nothing.
        enabled = this[key(accountId, ENABLED)]?.toBooleanStrictOrNull() ?: true,
        instructions = this[key(accountId, INSTRUCTIONS)].orEmpty(),
        mcpServers = this[key(accountId, MCP_SERVERS)]
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty(),
    )

    /** The account's key when there is an account, the device's otherwise. */
    private fun key(accountId: String?, base: String): Preferences.Key<String> =
        accountId?.let { accountScopedKey(it, base) } ?: stringPreferencesKey("$DEVICE_PREFIX$base")

    private companion object {
        const val ENABLED = "chat_profile_enabled"
        const val INSTRUCTIONS = "chat_profile_instructions"
        const val MCP_SERVERS = "chat_profile_mcp_servers"
        const val DEVICE_PREFIX = "device:"
    }
}
