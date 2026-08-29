package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_back
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_empty
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_send
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_title
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_working
import com.garfiec.librechat.feature.tasks.resources.tasks_stop
import com.garfiec.librechat.feature.tasks.util.ChatPart
import com.garfiec.librechat.feature.tasks.util.ChatTurn
import com.garfiec.librechat.feature.tasks.util.MissionChatState
import com.garfiec.librechat.feature.tasks.util.ToolState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One mission session, as a conversation. The whole history is replayed onto the screen and the reply
 * streams in token by token; the input at the bottom talks back to the same session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionChatScreen(
    sessionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MissionChatViewModel = koinViewModel { parametersOf(sessionId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.tasks_chat_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.tasks_chat_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            MissionChatInput(
                input = state.input,
                streaming = state.chat.streaming,
                sending = state.sending,
                sendError = state.sendError,
                onInput = viewModel::onInputChange,
                onSend = viewModel::send,
                onStop = viewModel::stop,
                onDismissError = viewModel::dismissSendError,
            )
        },
    ) { padding ->
        MissionChatBody(state = state.chat, contentPadding = padding)
    }
}

@Composable
private fun MissionChatBody(state: MissionChatState, contentPadding: PaddingValues) {
    Box(Modifier.padding(contentPadding).fillMaxSize()) {
        if (state.turns.isEmpty()) {
            Text(
                text = stringResource(Res.string.tasks_chat_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            return@Box
        }

        val listState = rememberLazyListState()
        // Follow the reply as it grows: a new turn, or more text on the last one, scrolls to the tail.
        LaunchedEffect(state.turns.size, tailLength(state)) {
            listState.animateScrollToItem((state.turns.size - 1).coerceAtLeast(0))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.turns.size) { index ->
                when (val turn = state.turns[index]) {
                    is ChatTurn.User -> UserBubble(turn.text)
                    is ChatTurn.Assistant -> AssistantBubble(turn, streaming = state.streaming && index == state.turns.lastIndex)
                }
            }
        }
    }
}

private fun tailLength(state: MissionChatState): Int {
    val last = state.turns.lastOrNull() as? ChatTurn.Assistant ?: return 0
    return last.parts.sumOf { part -> if (part is ChatPart.Text) part.text.length else 1 }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(BUBBLE_MAX_FRACTION),
        ) {
            Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AssistantBubble(turn: ChatTurn.Assistant, streaming: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(BUBBLE_MAX_FRACTION),
        ) {
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                turn.parts.forEach { part ->
                    when (part) {
                        is ChatPart.Text -> if (part.text.isNotBlank()) {
                            Text(part.text, style = MaterialTheme.typography.bodyMedium)
                        }
                        is ChatPart.Reasoning -> if (part.text.isNotBlank()) {
                            Text(
                                text = part.text,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MUTED_ALPHA),
                            )
                        }
                        is ChatPart.Tool -> ToolRow(part)
                    }
                }
                turn.failed?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (streaming && turn.parts.none { it is ChatPart.Text && it.text.isNotBlank() } && turn.failed == null) {
                    Text(
                        text = stringResource(Res.string.tasks_chat_working),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MUTED_ALPHA),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolRow(tool: ChatPart.Tool) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            imageVector = Icons.Outlined.Build,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MUTED_ALPHA),
        )
        Text(tool.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (tool.state) {
            ToolState.RUNNING -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
            ToolState.OK -> Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            ToolState.FAILED -> Icon(Icons.Filled.Close, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun MissionChatInput(
    input: String,
    streaming: Boolean,
    sending: Boolean,
    sendError: EngineFailureKind?,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Column {
            sendError?.let { kind ->
                // The send failed and the text was put back — say why, once, dismissible on tap.
                Text(
                    text = stringResource(kind.title()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismissError)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(Res.string.tasks_chat_hint)) },
                maxLines = MAX_INPUT_LINES,
            )
            Spacer(Modifier.width(0.dp))
            if (streaming) {
                FilledIconButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = stringResource(Res.string.tasks_stop))
                }
            } else {
                FilledIconButton(onClick = onSend, enabled = input.isNotBlank() && !sending) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(Res.string.tasks_chat_send))
                }
            }
            }
        }
    }
}

private const val BUBBLE_MAX_FRACTION = 0.85f
private const val MUTED_ALPHA = 0.7f
private const val MAX_INPUT_LINES = 6
