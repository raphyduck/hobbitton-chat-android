package com.librechat.android.feature.agents.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.librechat.android.core.model.mcp.McpTool
import com.librechat.android.feature.agents.R
import androidx.compose.ui.res.stringResource

/** Hierarchical MCP tool picker grouped by server with checkbox selection. */
@Composable
fun AgentMcpToolsSelector(
    mcpTools: List<McpTool>,
    selectedToolNames: Set<String>,
    onToolToggled: (toolName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

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
                text = stringResource(R.string.mcp_tools_count, selectedToolNames.size),
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
            )
        }

    val unknownServerLabel = stringResource(R.string.unknown_server)

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (mcpTools.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_mcp_tools),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    val grouped = remember(mcpTools) {
                        mcpTools.groupBy { it.serverName ?: unknownServerLabel }
                    }

                    grouped.forEach { (serverName, tools) ->
                        McpServerToolsGroup(
                            serverName = serverName,
                            tools = tools,
                            selectedToolNames = selectedToolNames,
                            onToolToggled = onToolToggled,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun McpServerToolsGroup(
    serverName: String,
    tools: List<McpTool>,
    selectedToolNames: Set<String>,
    onToolToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var serverExpanded by remember { mutableStateOf(true) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { serverExpanded = !serverExpanded }
                .padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = serverName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = if (serverExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (serverExpanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
            )
        }

        AnimatedVisibility(visible = serverExpanded) {
            Column(modifier = Modifier.padding(start = 8.dp)) {
                tools.forEach { tool ->
                    McpToolRow(
                        tool = tool,
                        isSelected = tool.name in selectedToolNames,
                        onToggle = { onToolToggled(tool.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun McpToolRow(
    tool: McpTool,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tool.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            val desc = tool.description
            if (desc != null) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}
