package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import librechat_mobile.feature.chat.generated.resources.Res
import librechat_mobile.feature.chat.generated.resources.*

data class ToolToggle(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

private val defaultTools = listOf(
    ToolToggle(ToolConstants.WEB_SEARCH, "Web Search", Icons.Default.Search),
    ToolToggle(ToolConstants.CODE_INTERPRETER, "Code", Icons.Default.Code),
    ToolToggle(ToolConstants.FILE_SEARCH, "File Search", Icons.Default.FindInPage),
)

/** Renders a toggleable toolbar for chat tool selection. Tool IDs must match backend tool identifiers. */
@Composable
fun ChatInputToolbar(
    enabledTools: Set<String>,
    onToggleTool: (String) -> Unit,
    modifier: Modifier = Modifier,
    mcpServers: List<McpServerDisplayData> = emptyList(),
    selectedMcpServerNames: Set<String> = emptySet(),
    onToggleMcpServer: (String) -> Unit = {},
    isCodeInterpreterAvailable: Boolean = true,
) {
    var showMoreTools by remember { mutableStateOf(false) }

    val visibleTools = remember(isCodeInterpreterAvailable) {
        if (isCodeInterpreterAvailable) {
            defaultTools
        } else {
            defaultTools.filter { it.id != ToolConstants.CODE_INTERPRETER }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleTools.forEach { tool ->
            FilterChip(
                selected = tool.id in enabledTools,
                onClick = { onToggleTool(tool.id) },
                label = {
                    Text(
                        text = tool.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.semantics {
                    contentDescription = if (tool.id in enabledTools) {
                        "${tool.label} enabled"
                    } else {
                        "${tool.label} disabled"
                    }
                },
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        if (mcpServers.isNotEmpty()) {
            McpServerSelector(
                servers = mcpServers,
                selectedServerNames = selectedMcpServerNames,
                onToggleServer = onToggleMcpServer,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        IconButton(
            onClick = { showMoreTools = true },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = stringResource(Res.string.cd_more_tools),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = showMoreTools,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ToolsDropdownMenu(
                enabledTools = enabledTools,
                onToggleTool = onToggleTool,
                onDismiss = { showMoreTools = false },
            )
        }
    }
}
