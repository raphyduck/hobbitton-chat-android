package com.garfiec.librechat.feature.tasks

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.ui.input.ChatInputDefaults
import com.garfiec.librechat.feature.tasks.components.MissionMarkdown
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_back
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_empty
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_send
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_title
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_working
import com.garfiec.librechat.feature.tasks.resources.tasks_retry
import com.garfiec.librechat.feature.tasks.resources.tasks_stop
import com.garfiec.librechat.feature.tasks.util.ChatPart
import com.garfiec.librechat.feature.tasks.util.ChatTurn
import com.garfiec.librechat.feature.tasks.util.MissionChatState
import com.garfiec.librechat.feature.tasks.util.ToolState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One mission session, as a conversation. The transcript is replayed on open and the reply streams in
 * token by token; the box at the bottom talks back to the same session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionChatScreen(
    sessionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    viewModel: MissionChatViewModel = koinViewModel { parametersOf(sessionId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { stringResource(Res.string.tasks_chat_title) }) },
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
        MissionChatBody(
            state = state,
            contentPadding = padding,
            onRetryHistory = viewModel::retryHistory,
        )
    }
}

@Composable
private fun MissionChatBody(
    state: MissionChatUiState,
    contentPadding: PaddingValues,
    onRetryHistory: () -> Unit,
) {
    Box(Modifier.padding(contentPadding).fillMaxSize()) {
        val historyFailure = state.historyError
        when {
            // The transcript is the conversation's past; while it loads, an empty screen would be a
            // lie about a session that has been talking for hours.
            state.loadingHistory && state.chat.turns.isEmpty() ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))

            historyFailure != null && state.chat.turns.isEmpty() -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(historyFailure.title()),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRetryHistory) { Text(stringResource(Res.string.tasks_retry)) }
            }

            state.chat.turns.isEmpty() -> Text(
                text = stringResource(Res.string.tasks_chat_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )

            else -> MissionTurns(state.chat)
        }
    }
}

@Composable
private fun MissionTurns(chat: MissionChatState) {
    val listState = rememberLazyListState()
    // Follow the answer as it grows: a new turn, or more text on the last one, scrolls to the tail.
    LaunchedEffect(chat.turns.size, tailLength(chat)) {
        listState.animateScrollToItem((chat.turns.size - 1).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            count = chat.turns.size,
            // Keyed by message id: a delta rewrites the last turn on every token, and without a key
            // Compose reuses by position and re-composes every bubble below it.
            key = { index -> chat.turns[index].key },
            contentType = { index -> chat.turns[index]::class },
        ) { index ->
            val turn = chat.turns[index]
            val live = chat.streaming && index == chat.turns.lastIndex
            when (turn) {
                is ChatTurn.User -> UserBubble(turn)
                is ChatTurn.Assistant -> AssistantTurn(turn, streaming = live)
            }
        }
    }
}

private fun tailLength(chat: MissionChatState): Int {
    val last = chat.turns.lastOrNull() ?: return 0
    val parts = when (last) {
        is ChatTurn.User -> last.parts
        is ChatTurn.Assistant -> last.parts
    }
    return parts.sumOf { part -> if (part is ChatPart.Text) part.text.length else 1 }
}

@Composable
private fun UserBubble(turn: ChatTurn.User) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(BUBBLE_MAX_FRACTION),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                turn.parts.filterIsInstance<ChatPart.Text>().forEach { part ->
                    if (part.text.isNotBlank()) {
                        MissionMarkdown(part.text, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

/**
 * The assistant's turn, rendered flat rather than in a bubble — the chat does the same: a long answer
 * inside a coloured box is harder to read than one that owns the width.
 */
@Composable
private fun AssistantTurn(turn: ChatTurn.Assistant, streaming: Boolean) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        turn.parts.forEach { part ->
            when (part) {
                is ChatPart.Text -> if (part.text.isNotBlank()) MissionMarkdown(part.text)
                is ChatPart.Reasoning -> if (part.text.isNotBlank()) {
                    Text(
                        text = part.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MUTED_ALPHA),
                    )
                }
                is ChatPart.Tool -> ToolRow(part)
            }
        }
        val silent = turn.parts.none { it is ChatPart.Text && it.text.isNotBlank() }
        if (streaming && silent) {
            Text(
                text = stringResource(Res.string.tasks_chat_working),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MUTED_ALPHA),
            )
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

/**
 * The composer, wearing the chat's clothes.
 *
 * Shape, fill, border and keyboard behaviour come from `:core:ui`'s [ChatInputDefaults] — the same
 * object the chat's own composer reads — so the two controls cannot drift apart the way they had by
 * 29/08/2026, when this screen shipped a bare `OutlinedTextField`. What is *not* shared is the
 * chat's composer itself: attachments, MCP pickers, voice, queueing and steering are all chat
 * concepts a mission session has none of, and a feature module cannot see another's code anyway.
 * Sharing the vocabulary is what is genuinely common; sharing the control would mean importing
 * machinery with no data behind it.
 */
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
        Column(Modifier.navigationBarsPadding()) {
            sendError?.let { kind ->
                // The send failed and the text was put back — say why, once, dismissible on tap.
                Text(
                    text = stringResource(kind.title()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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
                    shape = ChatInputDefaults.shape,
                    colors = ChatInputDefaults.textFieldColors(),
                    keyboardOptions = ChatInputDefaults.keyboardOptions,
                    maxLines = MAX_INPUT_LINES,
                )
                MissionSendButton(
                    streaming = streaming,
                    canSend = input.isNotBlank() && !sending,
                    onSend = onSend,
                    onStop = onStop,
                )
            }
        }
    }
}

/**
 * Send, or stop what is running — the chat's button, down to the 56 dp target and the error-coloured
 * stop. Animated across the swap for the same reason the chat animates it: the two states occupy the
 * same spot, and a hard cut reads as the button having been replaced rather than having changed.
 */
@Composable
private fun MissionSendButton(
    streaming: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    AnimatedContent(
        targetState = streaming,
        transitionSpec = { (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut()) },
        label = "mission_send_stop_toggle",
    ) { running ->
        if (running) {
            IconButton(
                onClick = onStop,
                modifier = Modifier.size(SEND_BUTTON_SIZE),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = stringResource(Res.string.tasks_stop),
                    modifier = Modifier.size(SEND_ICON_SIZE),
                )
            }
        } else {
            IconButton(
                onClick = onSend,
                modifier = Modifier.size(SEND_BUTTON_SIZE),
                enabled = canSend,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (canSend) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    contentColor = if (canSend) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(Res.string.tasks_chat_send),
                )
            }
        }
    }
}

private const val BUBBLE_MAX_FRACTION = 0.85f
private const val MUTED_ALPHA = 0.7f
private const val MAX_INPUT_LINES = 6

/** The chat's 56 dp touch target, so the two composers line up when you switch between them. */
private val SEND_BUTTON_SIZE = 56.dp
private val SEND_ICON_SIZE = 28.dp
