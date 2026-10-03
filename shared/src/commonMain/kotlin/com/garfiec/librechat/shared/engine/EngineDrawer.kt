package com.garfiec.librechat.shared.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.engine.EngineChatSummary
import com.garfiec.librechat.core.data.engine.EngineSessionKind
import com.garfiec.librechat.shared.resources.Res
import com.garfiec.librechat.shared.resources.engine_age_days
import com.garfiec.librechat.shared.resources.engine_age_hours
import com.garfiec.librechat.shared.resources.engine_age_minutes
import com.garfiec.librechat.shared.resources.engine_chat_running
import com.garfiec.librechat.shared.resources.engine_drawer_chat
import com.garfiec.librechat.shared.resources.engine_drawer_empty
import com.garfiec.librechat.shared.resources.engine_drawer_failed
import com.garfiec.librechat.shared.resources.engine_drawer_new_chat
import com.garfiec.librechat.shared.resources.engine_drawer_older
import com.garfiec.librechat.shared.resources.engine_drawer_previous_week
import com.garfiec.librechat.shared.resources.engine_drawer_recent
import com.garfiec.librechat.shared.resources.engine_drawer_retry
import com.garfiec.librechat.shared.resources.engine_drawer_settings
import com.garfiec.librechat.shared.resources.engine_drawer_task
import com.garfiec.librechat.shared.resources.engine_drawer_tasks
import com.garfiec.librechat.shared.resources.engine_drawer_today
import com.garfiec.librechat.shared.resources.engine_drawer_yesterday
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * The drawer of the engine shell (D-077): a new chat, the recent conversations, the Tasks tab, the
 * settings. The conversations are the chats and, since 02/10/2026, the tasks a person started
 * ([EngineChatSummary.kind]) — a task carries its tab's icon, a chat a bubble. The scheduler's own
 * runs stay in the Tasks tab.
 *
 * Since 03/10/2026 the conversations sit under day headers (Today, Yesterday, Previous 7 days,
 * Older — [groupRecent]) with a short age on the right, and a first load shows placeholder rows
 * instead of a progress bar over an empty list.
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
        // A refresh over a list already drawn: the thin bar is enough. A first load gets
        // placeholder rows instead, in the list below.
        if (state.loading && state.chats.isNotEmpty()) {
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
    // Read once per list: the headers and ages of one drawing agree on what « now » is.
    val now = remember(state.chats) { Clock.System.now().toEpochMilliseconds() }
    val groups = remember(state.chats, now) { groupRecent(state.chats, now, ::localUtcOffsetMillis) }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        when {
            // Loading first: a retry after a failure shows the placeholders, not the stale error.
            state.chats.isEmpty() && state.loading -> {
                item { RecentHeader(stringResource(Res.string.engine_drawer_recent)) }
                items(PLACEHOLDER_ROWS) { PlaceholderRow() }
            }
            state.failed && state.chats.isEmpty() -> {
                item { RecentHeader(stringResource(Res.string.engine_drawer_recent)) }
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = stringResource(Res.string.engine_drawer_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onRetry) { Text(stringResource(Res.string.engine_drawer_retry)) }
                    }
                }
            }
            state.chats.isEmpty() -> {
                item { RecentHeader(stringResource(Res.string.engine_drawer_recent)) }
                item {
                    Text(
                        text = stringResource(Res.string.engine_drawer_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            else -> groups.forEach { group ->
                item(key = "header-${group.day.name}") { RecentHeader(stringResource(group.day.label())) }
                items(group.chats, key = { it.sessionId }) { chat ->
                    RecentChatItem(
                        chat = chat,
                        nowMillis = now,
                        selected = chat.sessionId == activeSessionId,
                        onClick = { onOpenChat(chat) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun RecentHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * One conversation: its kind's icon (a bubble for a chat, the Tasks tab's for a task, so the titles
 * line up), the title, « Running » with a spinner while it answers, and how long ago it moved.
 */
@Composable
private fun RecentChatItem(
    chat: EngineChatSummary,
    nowMillis: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val task = chat.kind == EngineSessionKind.TASK
    NavigationDrawerItem(
        icon = {
            Icon(
                imageVector = if (task) Icons.Outlined.TaskAlt else Icons.Outlined.ChatBubbleOutline,
                contentDescription = stringResource(
                    if (task) Res.string.engine_drawer_task else Res.string.engine_drawer_chat,
                ),
            )
        },
        label = {
            Column {
                Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (chat.running) {
                    // The same spinner and word as the Tasks tab's rows and the tool calls.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(Res.string.engine_chat_running),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        badge = chat.lastActivityMillis?.let { lastActivity ->
            {
                Text(
                    text = recentAge(lastActivity, nowMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        selected = selected,
        onClick = onClick,
    )
}

/** A stand-in row while the first list loads: an icon's place and a title's, no shimmer. */
@Composable
private fun PlaceholderRow() {
    val fill = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = Modifier.fillMaxWidth().height(PLACEHOLDER_ROW_HEIGHT).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(24.dp).background(fill, CircleShape))
        Box(
            Modifier
                .fillMaxWidth(PLACEHOLDER_TITLE_WIDTH)
                .height(14.dp)
                .background(fill, RoundedCornerShape(4.dp)),
        )
    }
}

private fun RecentDay.label() = when (this) {
    RecentDay.TODAY -> Res.string.engine_drawer_today
    RecentDay.YESTERDAY -> Res.string.engine_drawer_yesterday
    RecentDay.PREVIOUS_WEEK -> Res.string.engine_drawer_previous_week
    RecentDay.OLDER -> Res.string.engine_drawer_older
}

/** « 5 min », « 3 h », « 2 d » — the Tasks tab's ages, compact like a messaging list. */
@Composable
private fun recentAge(lastActivityMillis: Long, nowMillis: Long): String {
    val minutes = ((nowMillis - lastActivityMillis) / MILLIS_PER_MINUTE).coerceAtLeast(0)
    return when {
        minutes < MINUTES_PER_HOUR -> stringResource(Res.string.engine_age_minutes, minutes)
        minutes < MINUTES_PER_DAY -> stringResource(Res.string.engine_age_hours, minutes / MINUTES_PER_HOUR)
        else -> stringResource(Res.string.engine_age_days, minutes / MINUTES_PER_DAY)
    }
}

private const val PLACEHOLDER_ROWS = 4
private const val PLACEHOLDER_TITLE_WIDTH = 0.7f
private val PLACEHOLDER_ROW_HEIGHT = 56.dp
private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24 * 60L
