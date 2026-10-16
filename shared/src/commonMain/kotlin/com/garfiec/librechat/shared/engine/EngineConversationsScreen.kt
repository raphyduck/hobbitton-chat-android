package com.garfiec.librechat.shared.engine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.engine.EngineChatSummary
import com.garfiec.librechat.shared.resources.Res
import com.garfiec.librechat.shared.resources.engine_conversations_read
import com.garfiec.librechat.shared.resources.engine_conversations_unread
import com.garfiec.librechat.shared.resources.engine_drawer_conversations
import com.garfiec.librechat.shared.resources.engine_drawer_empty
import com.garfiec.librechat.shared.resources.engine_drawer_failed
import com.garfiec.librechat.shared.resources.engine_drawer_retry
import com.garfiec.librechat.shared.resources.engine_settings_back
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Every conversation (16/10/2026), the scheduler's runs included, where the drawer keeps only the
 * ones the person started: the unread first, then the rest, each half by latest activity
 * (`inboxOrder`). A run counts as unread only when it had something to say, so a mission that
 * passes every half hour and finds nothing stays out of the top. Pushed over the conversation,
 * like Programmées; pull down to read it again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EngineConversationsScreen(
    state: EngineChatsState,
    onBack: () -> Unit,
    onOpenChat: (EngineChatSummary) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.engine_drawer_conversations)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.engine_settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.chats.isNotEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize().testTag("conversations_refresh"),
        ) {
            ConversationList(state = state, onOpenChat = onOpenChat, onRetry = onRefresh)
        }
    }
}

@Composable
private fun ConversationList(
    state: EngineChatsState,
    onOpenChat: (EngineChatSummary) -> Unit,
    onRetry: () -> Unit,
) {
    // Read once per list: the ages of one drawing agree on what « now » is.
    val now = remember(state.chats) { Clock.System.now().toEpochMilliseconds() }
    val (unread, read) = remember(state.chats) { state.chats.partition { it.unread } }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        when {
            state.chats.isEmpty() && state.loading -> items(PLACEHOLDER_ROWS) { PlaceholderRow() }
            state.failed && state.chats.isEmpty() -> item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(Res.string.engine_drawer_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(Res.string.engine_drawer_retry)) }
                }
            }
            state.chats.isEmpty() -> item { QuietLine(stringResource(Res.string.engine_drawer_empty)) }
            else -> {
                if (unread.isNotEmpty()) {
                    item(key = "header-unread") { RecentHeader(stringResource(Res.string.engine_conversations_unread)) }
                    items(unread, key = { it.sessionId }) { chat ->
                        RecentChatItem(chat = chat, nowMillis = now, selected = false, onClick = { onOpenChat(chat) })
                    }
                    if (read.isNotEmpty()) {
                        item(key = "header-read") { RecentHeader(stringResource(Res.string.engine_conversations_read)) }
                    }
                }
                items(read, key = { it.sessionId }) { chat ->
                    RecentChatItem(chat = chat, nowMillis = now, selected = false, onClick = { onOpenChat(chat) })
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

private const val PLACEHOLDER_ROWS = 6
