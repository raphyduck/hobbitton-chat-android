package com.librechat.android.feature.agents.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.librechat.android.feature.agents.AgentHandoffDisplayData
import librechat_android.feature.agents.generated.resources.Res
import librechat_android.feature.agents.generated.resources.*

@Composable
fun AgentHandoffConfig(
    handoffAgentIds: List<String>,
    availableAgents: List<AgentHandoffDisplayData>,
    onAddHandoff: (agentId: String) -> Unit,
    onRemoveHandoff: (agentId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.handoff_agents_count, handoffAgentIds.size),
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) stringResource(Res.string.cd_collapse) else stringResource(Res.string.cd_expand),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.handoff_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (handoffAgentIds.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.no_handoff_agents),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                handoffAgentIds.forEach { agentId ->
                    val agentDisplay = availableAgents.find { it.id == agentId }
                    val displayName = agentDisplay?.name ?: agentId
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(displayName) },
                        trailingIcon = {
                            IconButton(onClick = { onRemoveHandoff(agentId) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(Res.string.cd_remove_item, displayName),
                                )
                            }
                        },
                    )
                }

                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.add_handoff_agent))
                }
            }
        }
    }

    if (showAddDialog) {
        AddHandoffAgentDialog(
            availableAgents = availableAgents.filter { it.id !in handoffAgentIds },
            onDismiss = { showAddDialog = false },
            onSelect = { agentId ->
                onAddHandoff(agentId)
                showAddDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddHandoffAgentDialog(
    availableAgents: List<AgentHandoffDisplayData>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedAgent by remember { mutableStateOf<AgentHandoffDisplayData?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_handoff_agent)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (availableAgents.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.no_more_agents),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = selectedAgent?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(Res.string.select_agent)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                        ) {
                            availableAgents.forEach { agent ->
                                DropdownMenuItem(
                                    text = { Text(agent.name) },
                                    onClick = {
                                        selectedAgent = agent
                                        dropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedAgent?.let { onSelect(it.id) } },
                enabled = selectedAgent != null,
            ) {
                Text(stringResource(Res.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
