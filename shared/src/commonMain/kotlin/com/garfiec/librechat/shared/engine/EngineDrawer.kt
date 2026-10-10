package com.garfiec.librechat.shared.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garfiec.librechat.core.data.engine.EngineChatSummary
import com.garfiec.librechat.core.data.engine.EngineSessionKind
import com.garfiec.librechat.core.ui.input.ChatInputDefaults
import com.garfiec.librechat.shared.resources.Res
import com.garfiec.librechat.shared.resources.engine_age_days
import com.garfiec.librechat.shared.resources.engine_age_hours
import com.garfiec.librechat.shared.resources.engine_age_minutes
import com.garfiec.librechat.shared.resources.engine_chat_running
import com.garfiec.librechat.shared.resources.engine_drawer_account
import com.garfiec.librechat.shared.resources.engine_drawer_empty
import com.garfiec.librechat.shared.resources.engine_drawer_failed
import com.garfiec.librechat.shared.resources.engine_drawer_new_chat
import com.garfiec.librechat.shared.resources.engine_drawer_no_match
import com.garfiec.librechat.shared.resources.engine_drawer_older
import com.garfiec.librechat.shared.resources.engine_drawer_previous_week
import com.garfiec.librechat.shared.resources.engine_drawer_recent
import com.garfiec.librechat.shared.resources.engine_drawer_retry
import com.garfiec.librechat.shared.resources.engine_drawer_search
import com.garfiec.librechat.shared.resources.engine_drawer_settings
import com.garfiec.librechat.shared.resources.engine_drawer_task
import com.garfiec.librechat.shared.resources.engine_drawer_tasks
import com.garfiec.librechat.shared.resources.engine_drawer_today
import com.garfiec.librechat.shared.resources.engine_drawer_yesterday
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Who the drawer's foot names: the person the portal knows, and the platform they are on. Either
 * may be missing; the row then says what it can.
 */
data class DrawerAccount(
    val name: String? = null,
    val host: String? = null,
) {
    /** The letter in the round mark: the name's first, else the host's. */
    val initial: String
        get() = (name ?: host).orEmpty().trim().take(1).uppercase()
}

/**
 * The drawer of the engine shell (D-077), laid out as Claude's since lot 3 (10/10/2026): a search
 * over the titles, two short entries (a new chat, the Tasks tab), the recent conversations as bare
 * titles under quiet day headers, and the account at the foot, which opens the settings.
 *
 * The conversations are the chats and, since 02/10/2026, the tasks a person started
 * ([EngineChatSummary.kind]); a task wears a small tag rather than an icon, so the titles line up.
 * The scheduler's own runs stay in the Tasks tab. A conversation still answering carries a dot of
 * the accent; a first load shows placeholder rows instead of a progress bar over an empty list.
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
    account: DrawerAccount? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(modifier = modifier.fillMaxSize().padding(top = 12.dp)) {
        SearchField(query = query, onQueryChange = { query = it })
        Spacer(Modifier.height(6.dp))
        DrawerEntry(
            icon = Icons.Outlined.Edit,
            label = stringResource(Res.string.engine_drawer_new_chat),
            onClick = onNewChat,
            modifier = Modifier.testTag("drawer_new_chat"),
        )
        DrawerEntry(
            icon = Icons.Outlined.TaskAlt,
            label = stringResource(Res.string.engine_drawer_tasks),
            onClick = onOpenTasks,
            modifier = Modifier.testTag("drawer_tasks"),
        )
        // A refresh over a list already drawn: the thin bar is enough. A first load gets
        // placeholder rows instead, in the list below.
        if (state.loading && state.chats.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
        }
        RecentChats(
            state = state,
            query = query,
            activeSessionId = activeSessionId,
            onOpenChat = onOpenChat,
            onRetry = onRetry,
            modifier = Modifier.weight(1f),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AccountRow(account = account, onClick = onOpenSettings)
    }
}

/** A filter over the titles, local and instant: the list below shrinks as one types. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(ChatInputDefaults.containerColor, SearchShape)
            .border(1.dp, ChatInputDefaults.borderColor, SearchShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(Res.string.engine_drawer_search),
                            style = style,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            }
        },
    )
}

/** A short entry above the list: an icon and a word, 44 dp, no pill. */
@Composable
private fun DrawerEntry(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RowShape)
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RecentChats(
    state: EngineChatsState,
    query: String,
    activeSessionId: String?,
    onOpenChat: (EngineChatSummary) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read once per list: the headers and ages of one drawing agree on what « now » is.
    val now = remember(state.chats) { Clock.System.now().toEpochMilliseconds() }
    val shown = remember(state.chats, query) {
        val needle = query.trim()
        if (needle.isEmpty()) state.chats else state.chats.filter { it.title.contains(needle, ignoreCase = true) }
    }
    val groups = remember(shown, now) { groupRecent(shown, now, ::localUtcOffsetMillis) }
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
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
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
                item { QuietLine(stringResource(Res.string.engine_drawer_empty)) }
            }
            shown.isEmpty() -> {
                item { QuietLine(stringResource(Res.string.engine_drawer_no_match)) }
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
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun QuietLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

/**
 * One conversation: its title alone, a small « Tâche » tag when it is one, a dot of the accent while
 * it answers (the Tasks tab's « Running » is said for TalkBack), and how long ago it moved. The
 * open one sits on a sand fill.
 */
@Composable
private fun RecentChatItem(
    chat: EngineChatSummary,
    nowMillis: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val fill = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RowShape)
            .background(fill, RowShape)
            .clickable(onClick = onClick)
            .heightIn(min = ROW_HEIGHT)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            chat.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // All the room the tag, the dot and the age leave: the title is what one reads.
            modifier = Modifier.weight(1f),
        )
        if (chat.kind == EngineSessionKind.TASK) {
            Text(
                text = stringResource(Res.string.engine_drawer_task),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, TagShape)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (chat.running) {
            val running = stringResource(Res.string.engine_chat_running)
            Box(
                Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .semantics { contentDescription = running },
            )
        }
        chat.lastActivityMillis?.let { lastActivity ->
            Text(
                text = recentAge(lastActivity, nowMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The foot: the person's initial in the serif on a brick disc, their name and their platform's
 * host, and the settings behind the whole row, where Claude puts the account.
 */
@Composable
private fun AccountRow(account: DrawerAccount?, onClick: () -> Unit) {
    val primary = account?.name ?: account?.host ?: stringResource(Res.string.engine_drawer_settings)
    val secondary = account?.host?.takeIf { account.name != null }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("drawer_settings"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = account?.initial?.ifBlank { null } ?: "B",
                // The serif's voice, at a size that sits in a 32 dp disc.
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                primary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondary != null) {
                Text(
                    secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.Outlined.Settings,
            contentDescription = stringResource(Res.string.engine_drawer_account),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A stand-in row while the first list loads: a title's place, no shimmer. */
@Composable
private fun PlaceholderRow() {
    val fill = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .fillMaxWidth(PLACEHOLDER_TITLE_WIDTH)
                .height(12.dp)
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
private val ROW_HEIGHT = 40.dp
private val RowShape = RoundedCornerShape(10.dp)
private val TagShape = RoundedCornerShape(5.dp)
private val SearchShape = RoundedCornerShape(12.dp)
private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24 * 60L
