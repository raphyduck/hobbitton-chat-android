package com.garfiec.librechat.feature.chat.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import com.garfiec.librechat.core.common.ToolConstants
import librechat_mobile.feature.chat.generated.resources.Res
import librechat_mobile.feature.chat.generated.resources.*

private data class ToolMenuItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

private val extraTools = listOf(
    ToolMenuItem("dalle", "DALL-E", Icons.Default.Image),
    ToolMenuItem(ToolConstants.CODE_INTERPRETER, "Code Interpreter", Icons.Default.Code),
    ToolMenuItem(ToolConstants.WEB_SEARCH, "Web Search", Icons.Default.Search),
)

@Composable
fun ToolsDropdownMenu(
    enabledTools: Set<String>,
    onToggleTool: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isCodeInterpreterAvailable: Boolean = true,
) {
    val visibleTools = remember(isCodeInterpreterAvailable) {
        if (isCodeInterpreterAvailable) {
            extraTools
        } else {
            extraTools.filter { it.id != ToolConstants.CODE_INTERPRETER }
        }
    }

    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        visibleTools.forEach { tool ->
            val isEnabled = tool.id in enabledTools
            DropdownMenuItem(
                text = {
                    Text(
                        text = tool.label,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onClick = {
                    onToggleTool(tool.id)
                },
                leadingIcon = {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = if (isEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                trailingIcon = {
                    if (isEnabled) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(Res.string.cd_tool_enabled),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }
    }
}
