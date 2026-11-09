package com.garfiec.librechat.shared.engine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.engine.EngineChatSummary
import com.garfiec.librechat.feature.tasks.share.PendingShare
import com.garfiec.librechat.shared.resources.Res
import com.garfiec.librechat.shared.resources.engine_drawer_new_chat
import com.garfiec.librechat.shared.resources.engine_drawer_no_match
import com.garfiec.librechat.shared.resources.engine_drawer_search
import com.garfiec.librechat.shared.resources.engine_share_cancel
import com.garfiec.librechat.shared.resources.engine_share_files
import com.garfiec.librechat.shared.resources.engine_share_files_and_text
import com.garfiec.librechat.shared.resources.engine_share_text
import com.garfiec.librechat.shared.resources.engine_share_title
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * Where a share from another app goes (09/11/2026, Raphaël's rule: a new conversation or an
 * existing one, chosen at the time of the share): a sheet over whatever is shown, « Nouvelle
 * conversation » first, then the recent conversations as the drawer lists them, with the drawer's
 * search over every session. Dismissed, the share is dropped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareTargetSheet(
    share: PendingShare,
    chats: EngineChatsState,
    search: EngineChatsState?,
    onSearch: (String) -> Unit,
    onNewChat: () -> Unit,
    onChat: (EngineChatSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    val listed = remember(chats, search, query) {
        when {
            query.isBlank() -> chats.chats
            search != null -> search.chats
            else -> chats.chats.filter { it.title.contains(query, ignoreCase = true) }
        }
    }
    val now = remember(listed) { Clock.System.now().toEpochMilliseconds() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = Modifier.testTag("share_target")) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                stringResource(Res.string.engine_share_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                shareSummary(share),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearch(it)
                },
                placeholder = { Text(stringResource(Res.string.engine_drawer_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = SHEET_LIST_MAX)) {
                item(key = "new") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNewChat)
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                            .testTag("share_new_chat"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(Res.string.engine_drawer_new_chat),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                when {
                    listed.isEmpty() && (chats.loading || (query.isNotBlank() && search?.loading == true)) ->
                        items(PLACEHOLDER_ROWS) { PlaceholderRow() }
                    listed.isEmpty() && query.isNotBlank() -> item { QuietLine(stringResource(Res.string.engine_drawer_no_match)) }
                    else -> items(listed, key = { it.sessionId }) { chat ->
                        RecentChatItem(chat = chat, nowMillis = now, selected = false, onClick = { onChat(chat) })
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(Res.string.engine_share_cancel))
            }
        }
    }
}

@Composable
private fun shareSummary(share: PendingShare): String {
    val files = share.attachments.size
    val text = !share.text.isNullOrBlank()
    return when {
        files > 0 && text -> stringResource(Res.string.engine_share_files_and_text, files)
        files > 0 -> stringResource(Res.string.engine_share_files, files)
        else -> stringResource(Res.string.engine_share_text)
    }
}

private val SHEET_LIST_MAX = 420.dp
private const val PLACEHOLDER_ROWS = 4
