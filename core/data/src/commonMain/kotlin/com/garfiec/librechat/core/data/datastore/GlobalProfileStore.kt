package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.model.chat.GlobalProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Where the global profile is kept.
 *
 * **Account-scoped**, like the last used model and for the same reason: the MCP servers of one
 * server are not those of another, and instructions written for a work account have no business
 * riding on a personal one. The `acct:` prefix also means the logout purge sweeps them with
 * everything else, without this file having to remember to.
 */
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

class GlobalProfileStore(
    private val dataStore: DataStore<Preferences>,
    private val activeAccountProvider: ActiveAccountProvider,
) : GlobalProfileSource {

    /**
     * The profile of the signed-in account. Emits nothing while the identity is still resolving —
     * a default emitted during that window would read as « no profile » and send a first message
     * without one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val profile: Flow<GlobalProfile> = activeAccountProvider.state.flatMapLatest { state ->
        when (state) {
            AccountState.Warming -> emptyFlow()
            is AccountState.Resolved ->
                state.id?.let { id -> dataStore.data.map { prefs -> prefs.readProfile(id.value) } }
                    ?: flowOf(GlobalProfile.NONE)
        }
    }

    /**
     * The profile as it stands, for the send path.
     *
     * Reads rather than collects: a chat request needs one value now, and a `Flow` there would mean
     * a subscription per message.
     */
    override suspend fun current(): GlobalProfile {
        val accountId = activeAccountProvider.currentAccountId()?.value ?: return GlobalProfile.NONE
        return dataStore.data.first().readProfile(accountId)
    }

    suspend fun save(profile: GlobalProfile) {
        val accountId = activeAccountProvider.currentAccountId()?.value ?: return
        dataStore.edit { prefs ->
            prefs[accountScopedKey(accountId, ENABLED)] = profile.enabled.toString()
            prefs[accountScopedKey(accountId, INSTRUCTIONS)] = profile.instructions
            // Joined on a newline rather than a comma: a server name may not contain one, and this
            // survives a name with a comma in it — which `GET /api/mcp/servers` is free to return.
            prefs[accountScopedKey(accountId, MCP_SERVERS)] = profile.mcpServers.joinToString("\n")
        }
    }

    private fun Preferences.readProfile(accountId: String) = GlobalProfile(
        // Absent means ON. A profile someone filled in and never switched on would be the most
        // confusing possible default — it would look configured and do nothing.
        enabled = this[accountScopedKey(accountId, ENABLED)]?.toBooleanStrictOrNull() ?: true,
        instructions = this[accountScopedKey(accountId, INSTRUCTIONS)].orEmpty(),
        mcpServers = this[accountScopedKey(accountId, MCP_SERVERS)]
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty(),
    )

    private companion object {
        const val ENABLED = "chat_profile_enabled"
        const val INSTRUCTIONS = "chat_profile_instructions"
        const val MCP_SERVERS = "chat_profile_mcp_servers"
    }
}
