package com.garfiec.librechat.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.GlobalProfileStore
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.model.chat.GlobalProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One MCP server as the form shows it: its name, what to call it on screen, and whether the profile
 * equips it.
 *
 * [reachable] is false for a server the backend lists but could not connect to. It is shown anyway,
 * ticked if it was ticked: hiding it would silently drop it from the profile the next time this
 * screen saved, and a server that is down for an hour is not a server someone stopped wanting.
 */
data class ProfileServer(
    val name: String,
    val label: String,
    val selected: Boolean,
    val reachable: Boolean,
)

data class GlobalProfileUiState(
    val loading: Boolean = true,
    val enabled: Boolean = true,
    val instructions: String = "",
    val servers: List<ProfileServer> = emptyList(),
    /** Set when the server list could not be fetched — the ticks still work, from stored names. */
    val serversUnavailable: Boolean = false,
    val saving: Boolean = false,
)

/**
 * The global profile: instructions and MCP servers that ride on every conversation, whatever model
 * is chosen.
 *
 * Saves on every edit rather than behind a button. There is no invalid state to guard here — a
 * blank prompt and no server is simply « no profile » — and a settings screen that loses what was
 * typed because it was left without a tap is the more likely failure by far.
 */
class GlobalProfileViewModel(
    private val store: GlobalProfileStore,
    private val mcpRepository: McpRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GlobalProfileUiState())
    val state: StateFlow<GlobalProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val profile = store.profile.first()
            val listed = mcpRepository.listServers()
            val servers = when (listed) {
                is Result.Success -> listed.data.map { server ->
                    ProfileServer(
                        name = server.name,
                        label = server.title?.takeIf { it.isNotBlank() } ?: server.name,
                        selected = server.name in profile.mcpServers,
                        reachable = server.isConnected,
                    )
                }
                // Stored names, so the ticks survive an offline opening and a save from this screen
                // cannot quietly empty the profile.
                else -> profile.mcpServers.map { name ->
                    ProfileServer(name = name, label = name, selected = true, reachable = false)
                }
            }
            val known = servers.map { it.name }.toSet()
            _state.value = GlobalProfileUiState(
                loading = false,
                enabled = profile.enabled,
                instructions = profile.instructions,
                // A server the profile names but the backend no longer lists is kept, ticked: it is
                // more often a backend that is starting up than a server someone removed.
                servers = servers + profile.mcpServers.filterNot { it in known }.map { name ->
                    ProfileServer(name = name, label = name, selected = true, reachable = false)
                },
                serversUnavailable = listed !is Result.Success,
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
        persist()
    }

    fun setInstructions(text: String) {
        _state.update { it.copy(instructions = text) }
        persist()
    }

    fun toggleServer(name: String, selected: Boolean) {
        _state.update { current ->
            current.copy(
                servers = current.servers.map {
                    if (it.name == name) it.copy(selected = selected) else it
                },
            )
        }
        persist()
    }

    private fun persist() {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            runCatching {
                store.save(
                    GlobalProfile(
                        enabled = current.enabled,
                        instructions = current.instructions,
                        mcpServers = current.servers.filter { it.selected }.map { it.name }.toSet(),
                    ),
                )
            }.onFailure { failure ->
                Logger.w(failure, tag = "Settings") { "Could not save the global profile" }
            }
            _state.update { it.copy(saving = false) }
        }
    }
}
