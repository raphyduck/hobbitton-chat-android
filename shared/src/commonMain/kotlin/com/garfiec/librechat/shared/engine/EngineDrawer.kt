package com.garfiec.librechat.shared.engine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.engine.EngineChatSummary
import com.garfiec.librechat.core.data.engine.EngineSessionKind
import com.garfiec.librechat.shared.resources.Res
import com.garfiec.librechat.shared.resources.engine_chat_running
import com.garfiec.librechat.shared.resources.engine_drawer_empty
import com.garfiec.librechat.shared.resources.engine_drawer_failed
import com.garfiec.librechat.shared.resources.engine_drawer_new_chat
import com.garfiec.librechat.shared.resources.engine_drawer_recent
import com.garfiec.librechat.shared.resources.engine_drawer_retry
import com.garfiec.librechat.shared.resources.engine_drawer_settings
import com.garfiec.librechat.shared.resources.engine_drawer_task
import com.garfiec.librechat.shared.resources.engine_drawer_tasks
import org.jetbrains.compose.resources.stringResource

/**
 * The drawer of the engine shell (D-077): a new chat, the recent conversations, the Tasks tab, the
 * settings. The conversations are the chats and, since 02/10/2026, the tasks a person started
 * ([EngineChatSummary.kind]) — a task carries its tab's icon. The scheduler's own runs stay in the
 * Tasks tab.
 */
@Composable
internal fun EngineDrawerContent(
    state: EngineChatsState,
    activeSessionId: String?,
    onNewChat: () -> Unit,
    onOpenChat: (EngineChatSummary) -> Unit,
    onOpenTasks: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.engine_drawer_new_chat)) },
            icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            selected = false,
            onClick = onNewChat,
            modifier = Modifier.testTag("drawer_new_chat"),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.engine_drawer_tasks)) },
            icon = { Icon(Icons.Outlined.TaskAlt, contentDescription = null) },
            selected = false,
            onClick = onOpenTasks,
            modifier = Modifier.testTag("drawer_tasks"),
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = stringResource(Res.string.engine_drawer_recent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
        RecentChats(
            state = state,
            activeSessionId = activeSessionId,
            onOpenChat = onOpenChat,
            onRetry = onRetry,
            modifier = Modifier.weight(1f),
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.engine_drawer_settings)) },
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            selected = false,
            onClick = onOpenSettings,
            modifier = Modifier.testTag("drawer_settings"),
        )
    }
}

@Composable
private fun RecentChats(
    state: EngineChatsState,
    activeSessionId: String?,
    onOpenChat: (EngineChatSummary) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        when {
            state.failed && state.chats.isEmpty() -> item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(Res.string.engine_drawer_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(Res.string.engine_drawer_retry)) }
                }
            }
            state.chats.isEmpty() && !state.loading -> item {
                Text(
                    text = stringResource(Res.string.engine_drawer_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            else -> items(state.chats, key = { it.sessionId }) { chat ->
                // A task carries its tab's icon; a chat, the drawer's default, has none.
                val taskIcon: (@Composable () -> Unit)? = if (chat.kind == EngineSessionKind.TASK) {
                    {
                        Icon(Icons.Outlined.TaskAlt, contentDescription = stringResource(Res.string.engine_drawer_task))
                    }
                } else {
                    null
                }
                NavigationDrawerItem(
                    icon = taskIcon,
                    label = {
                        Column {
                            Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (chat.running) {
                                Text(
                                    text = stringResource(Res.string.engine_chat_running),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    selected = chat.sessionId == activeSessionId,
                    onClick = { onOpenChat(chat) },
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
