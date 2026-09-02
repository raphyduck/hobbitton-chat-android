package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.profile_agents_note
import com.garfiec.librechat.feature.settings.resources.profile_enabled
import com.garfiec.librechat.feature.settings.resources.profile_enabled_desc
import com.garfiec.librechat.feature.settings.resources.profile_instructions
import com.garfiec.librechat.feature.settings.resources.profile_instructions_desc
import com.garfiec.librechat.feature.settings.resources.profile_instructions_placeholder
import com.garfiec.librechat.feature.settings.resources.profile_server_unreachable
import com.garfiec.librechat.feature.settings.resources.profile_servers
import com.garfiec.librechat.feature.settings.resources.profile_servers_desc
import com.garfiec.librechat.feature.settings.resources.profile_servers_empty
import com.garfiec.librechat.feature.settings.resources.profile_servers_unavailable
import com.garfiec.librechat.feature.settings.viewmodel.GlobalProfileViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The one place a profile is configured — for every model, and for both surfaces.
 *
 * LibreChat attaches instructions and tools to an agent, and an agent pins a model, so the same
 * setup would otherwise be repeated for each of the catalogue's models. The Agent engine attaches a
 * charter to a métier profile decided server-side. Written here once, it is folded into every send
 * of both: `promptPrefix` in a conversation, `system` on a mission's turn.
 *
 * Everything saves as it is edited: there is no invalid state to guard against, and a settings pane
 * that loses what was typed because it was left without a tap is the likelier failure.
 */
@Composable
fun GlobalProfileSection(
    modifier: Modifier = Modifier,
    viewModel: GlobalProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.profile_enabled),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(Res.string.profile_enabled_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
        }

        OutlinedTextField(
            value = state.instructions,
            onValueChange = viewModel::setInstructions,
            label = { Text(stringResource(Res.string.profile_instructions)) },
            placeholder = { Text(stringResource(Res.string.profile_instructions_placeholder)) },
            supportingText = { Text(stringResource(Res.string.profile_instructions_desc)) },
            enabled = state.enabled,
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(Res.string.profile_servers), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(Res.string.profile_servers_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.serversUnavailable) {
            Text(
                stringResource(Res.string.profile_servers_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (!state.loading && state.servers.isEmpty()) {
            Text(
                stringResource(Res.string.profile_servers_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.servers.forEach { server ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = server.selected,
                    onCheckedChange = { viewModel.toggleServer(server.name, it) },
                    enabled = state.enabled,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(server.label, style = MaterialTheme.typography.bodyMedium)
                    // A server that is down is shown, and stays ticked: hiding it would drop it from
                    // the profile on the next save, which is not what « unreachable for an hour »
                    // should mean.
                    if (!server.reachable) {
                        Text(
                            stringResource(Res.string.profile_server_unreachable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Text(
            stringResource(Res.string.profile_agents_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
