package com.garfiec.librechat.feature.tasks

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.garfiec.librechat.core.data.datastore.MissionReadingPosition
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.ui.input.ChatInputDefaults
import com.garfiec.librechat.core.ui.markdown.StreamingWaitIndicator
import com.garfiec.librechat.feature.tasks.components.ConnectorPickerSheet
import com.garfiec.librechat.feature.tasks.components.DisclosureRow
import com.garfiec.librechat.feature.tasks.components.Explanation
import com.garfiec.librechat.feature.tasks.components.MissionMarkdown
import com.garfiec.librechat.feature.tasks.components.ModelPickerSheet
import com.garfiec.librechat.feature.tasks.components.rememberMissionAttachmentPicker
import com.garfiec.librechat.feature.tasks.components.rememberMissionAudioPicker
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_attach_audio
import com.garfiec.librechat.feature.tasks.resources.tasks_attach_photo
import com.garfiec.librechat.feature.tasks.resources.tasks_attached_photo
import com.garfiec.librechat.feature.tasks.resources.tasks_attachment_remove
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_back
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_collapse
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_connector_count
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_connectors_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_empty
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_expand
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_models_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_no_connector
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_output_truncated
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_reasoning
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_send
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_title
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_tool_count
import com.garfiec.librechat.feature.tasks.resources.tasks_connectors
import com.garfiec.librechat.feature.tasks.resources.tasks_model_default_short
import com.garfiec.librechat.feature.tasks.resources.tasks_retry
import com.garfiec.librechat.feature.tasks.resources.tasks_stop
import com.garfiec.librechat.feature.tasks.resources.tasks_transcription_failed
import com.garfiec.librechat.feature.tasks.util.ChatBlock
import com.garfiec.librechat.feature.tasks.util.ChatPart
import com.garfiec.librechat.feature.tasks.util.ChatTurn
import com.garfiec.librechat.feature.tasks.util.MissionChatState
import com.garfiec.librechat.feature.tasks.util.StagedAttachment
import com.garfiec.librechat.feature.tasks.util.ToolState
import com.garfiec.librechat.feature.tasks.util.asBlocks
import com.garfiec.librechat.feature.tasks.util.hasFailure
import com.garfiec.librechat.feature.tasks.util.hasReasoning
import com.garfiec.librechat.feature.tasks.util.hint
import com.garfiec.librechat.feature.tasks.util.isRunning
import com.garfiec.librechat.feature.tasks.util.title
import com.garfiec.librechat.feature.tasks.util.toolCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
                state = state,
                onInput = viewModel::onInputChange,
                onSend = viewModel::send,
                onStop = viewModel::stop,
                onDismissError = viewModel::dismissSendError,
                onToggleConnector = viewModel::toggleConnector,
                onSelectModel = viewModel::selectModel,
                onRetryCatalogue = viewModel::retryCatalogue,
                onAddAttachments = viewModel::addAttachments,
                onRemoveAttachment = viewModel::removeAttachment,
                onTranscribeAudio = viewModel::transcribeAudio,
                onDismissTranscriptionError = viewModel::dismissTranscriptionError,
            )
        },
    ) { padding ->
        MissionChatBody(
            state = state,
            contentPadding = padding,
            onRetryHistory = viewModel::retryHistory,
            onRememberPosition = viewModel::rememberPosition,
        )
    }
}

@Composable
private fun MissionChatBody(
    state: MissionChatUiState,
    contentPadding: PaddingValues,
    onRetryHistory: () -> Unit,
    onRememberPosition: (index: Int, offset: Int) -> Unit,
) {
    Box(Modifier.padding(contentPadding).fillMaxSize()) {
        val historyFailure = state.historyError
        when {
            // The transcript is the conversation's past; while it loads, an empty screen would be a
            // lie about a session that has been talking for hours.
            state.loadingHistory && state.chat.turns.isEmpty() ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))

            historyFailure != null && state.chat.turns.isEmpty() -> Explanation(
                title = stringResource(historyFailure.title()),
                hint = historyFailure.hint()?.let { stringResource(it) },
                action = stringResource(Res.string.tasks_retry) to onRetryHistory,
            )

            state.chat.turns.isEmpty() -> Text(
                text = stringResource(Res.string.tasks_chat_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )

            else -> MissionTurns(
                chat = state.chat,
                fontScale = state.fontScale,
                restoredPosition = state.restoredPosition,
                positionKnown = state.positionKnown,
                onRememberPosition = onRememberPosition,
            )
        }
    }
}

@Composable
private fun MissionTurns(
    chat: MissionChatState,
    fontScale: Float,
    restoredPosition: MissionReadingPosition?,
    positionKnown: Boolean,
    onRememberPosition: (index: Int, offset: Int) -> Unit,
) {
    val listState = rememberLazyListState()
    // Open where the reader left off. Once — hence the flag, saved across rotation: a second
    // restore would yank the list back out from under someone who has since scrolled.
    //
    // It waits on BOTH the stored position having been read and the transcript having landed,
    // because scrolling to item 40 of an empty list is a no-op the follow effect below then
    // finishes by dropping to the tail. Without a saved position the tail IS the right place, and
    // that is what this screen did for everyone before 31/08/2026.
    var restored by rememberSaveable(chat.turns.isNotEmpty()) { mutableStateOf(false) }
    LaunchedEffect(positionKnown, chat.turns.isNotEmpty()) {
        if (restored || !positionKnown || chat.turns.isEmpty()) return@LaunchedEffect
        restoredPosition?.let { listState.scrollToItem(it.index, it.offset) }
        restored = true
    }
    // Remember where they are, debounced: the position is written as they scroll, and a store write
    // per frame would be one per pixel. `collectLatest` cancels the pending delay on the next
    // change, so only a pause writes.
    //
    // `rememberUpdatedState` because the effect restarts on `restored` alone: capturing the lambda
    // directly would pin whichever instance was current when the effect started, and a recomposed
    // parent would then be writing through a stale reference.
    val remember by rememberUpdatedState(onRememberPosition)
    LaunchedEffect(listState, restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) ->
                delay(POSITION_SETTLE_MS)
                remember(index, offset)
            }
    }
    // Follow the answer as it grows — but only while the reader is already at the tail. An
    // unconditional scroll stole the list from anyone reading back through a streaming mission:
    // every token snapped the screen to the bottom. The chat gates its follow the same way.
    //
    // Gated on `restored` too, or the very first emission would scroll to the bottom before the
    // saved position has been applied — the follow reads an empty `visibleItemsInfo` as « at the
    // tail », which is exactly the state a list that has not drawn yet is in.
    LaunchedEffect(chat.turns.size, tailLength(chat), restored) {
        if (!restored) return@LaunchedEffect
        val info = listState.layoutInfo
        val nearTail = info.visibleItemsInfo.lastOrNull()
            ?.let { it.index >= info.totalItemsCount - 2 } ?: true
        if (nearTail) listState.animateScrollToItem((chat.turns.size - 1).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // The chat's rhythm: 16 dp gutters, 16 dp between messages (its 2 x 8 dp bubble padding).
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
            // Per TURN, not around the LazyColumn. A SelectionContainer only tracks what is
            // composed, and a lazy list recycles: one container around the whole list loses the
            // selection the moment a scroll drops its anchor off screen. The chat scopes its own
            // the same way — one per message. A transcript that could not be selected at all is
            // what shipped until 31/08/2026.
            SelectionContainer {
                when (turn) {
                    is ChatTurn.User -> UserBubble(turn, fontScale)
                    is ChatTurn.Assistant -> AssistantTurn(turn, streaming = live, fontScale = fontScale)
                }
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
private fun UserBubble(turn: ChatTurn.User, fontScale: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            // The chat's own bubble: secondaryContainer, chosen there over primaryContainer for
            // dark-mode contrast — same reason, same colour here. Shape and the 12 dp inner
            // padding are the chat's too (`BubbleShape`, `MessageBubble`).
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(16.dp),
            // Wraps its content instead of always claiming a fixed fraction: « ok » used to ship
            // in a bubble 85 % of the screen wide.
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                turn.parts.forEach { part ->
                    when (part) {
                        is ChatPart.Attachment -> AttachmentContent(part)
                        is ChatPart.Text ->
                            if (part.text.isNotBlank()) {
                                MissionMarkdown(
                                    part.text,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontScale = fontScale,
                                )
                            }
                        else -> Unit
                    }
                }
            }
        }
    }
}

/**
 * The assistant's turn: the answer at full width, the work that produced it folded away.
 *
 * Flat rather than in a bubble — the chat does the same: a long answer inside a coloured box is
 * harder to read than one that owns the width.
 *
 * Reasoning and tool calls arrive folded, like the chat's activity blocks. A mission's turn is
 * mostly process — nine tool calls and a paragraph of thinking around two sentences — and shipping
 * it flat on 30/08/2026 buried the part anyone actually reads.
 */
@Composable
private fun AssistantTurn(turn: ChatTurn.Assistant, streaming: Boolean, fontScale: Float) {
    val blocks = remember(turn.parts) { turn.parts.asBlocks() }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The cursor belongs to the LAST prose block, not to the last block: a turn that ends on a
        // folded activity has nowhere visible to put an insertion point, and the answer above it is
        // still where the next character lands.
        val lastProse = blocks.indexOfLast { it is ChatBlock.Prose }
        blocks.forEachIndexed { index, block ->
            when (block) {
                is ChatBlock.Media -> AttachmentContent(block.part)
                is ChatBlock.Prose -> MissionMarkdown(
                    text = block.part.text,
                    fontScale = fontScale,
                    trailingCursor = streaming && index == lastProse,
                )
                is ChatBlock.Activity -> ActivityBlock(block)
            }
        }
        // Before the first delta there is no insertion point, so the wait indicator stands in for
        // the cursor — the chat's own rule, and its own three dots.
        if (streaming && lastProse < 0) {
            StreamingWaitIndicator()
        }
    }
}

/**
 * The work behind an answer, folded.
 *
 * Two states are **never** hidden by the fold, and they are the reason the header says more than a
 * count: a tool still running (a mission waiting on one looks exactly like a mission that stopped)
 * and a tool that failed (a failure folded away is a failure nobody reads). Both surface on the
 * closed header — spinner and error colour — so folding costs no information one would act on.
 *
 * `rememberSaveable` keyed on the block, so a fold the reader opened survives a recomposition, and
 * a delta appending to the turn does not snap it shut under their thumb.
 */
@Composable
private fun ActivityBlock(block: ChatBlock.Activity) {
    var expanded by rememberSaveable(block.key) { mutableStateOf(false) }
    val failed = block.hasFailure()
    val running = block.isRunning()

    Column(Modifier.fillMaxWidth()) {
        // The chat's activity header, measure for measure (`ActivityGroup`): 16 dp Build icon at
        // full onSurfaceVariant, bodyMedium label, 8 dp gaps, a 40 dp touch target. What the chat
        // does NOT have and this keeps: a spinner while a tool still runs and error colour on a
        // failure — the two states a fold must never hide.
        DisclosureRow(
            label = activityLabel(block),
            expanded = expanded,
            onToggle = { expanded = !expanded },
            labelColor = if (failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            leading = {
                when {
                    running -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    failed -> Icon(
                        Icons.Filled.Close,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    else -> Icon(
                        Icons.Filled.Build,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        // Animated like the chat's: a hard pop reads as the list having jumped, not as a fold.
        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                Modifier.padding(start = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                block.parts.forEach { part ->
                    when (part) {
                        is ChatPart.Tool -> ToolRow(part)
                        is ChatPart.Reasoning -> Text(
                            text = part.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Neither ever reaches an activity group: prose and attachments open
                        // their own blocks in asBlocks.
                        is ChatPart.Text -> Unit
                        is ChatPart.Attachment -> Unit
                    }
                }
            }
        }
    }
}

/** « 3 outils · réflexion » — enough to decide whether opening it is worth it. */
@Composable
private fun activityLabel(block: ChatBlock.Activity): String {
    val tools = block.toolCount()
    val parts = buildList {
        if (tools > 0) add(stringResource(Res.string.tasks_chat_tool_count, tools))
        if (block.hasReasoning()) add(stringResource(Res.string.tasks_chat_reasoning))
    }
    return parts.joinToString(" · ").ifEmpty { stringResource(Res.string.tasks_chat_reasoning) }
}

/**
 * One tool call — its name and outcome, and, when opened, what it was called with and what it
 * answered.
 *
 * The payload is behind a second fold on purpose. A tool's output runs to a measured 51 000
 * characters, so unfolding it with the activity block would bury the answer the block was folded to
 * protect. A call with neither arguments nor output does not open at all: an empty drawer with a
 * chevron on it is a promise the row cannot keep.
 */
@Composable
private fun ToolRow(tool: ChatPart.Tool) {
    val hasPayload = tool.arguments.isNotEmpty() || tool.output != null
    var open by rememberSaveable(tool.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = if (hasPayload) {
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { open = !open }
            } else {
                Modifier.fillMaxWidth()
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Build,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                tool.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
            when (tool.state) {
                ToolState.RUNNING -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                ToolState.OK -> Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                ToolState.FAILED -> Icon(Icons.Filled.Close, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
            }
            if (hasPayload) {
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (open) Res.string.tasks_chat_collapse else Res.string.tasks_chat_expand,
                    ),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(visible = open, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier.padding(start = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tool.arguments.forEach { argument ->
                    Text(
                        text = argument.name + " : " + argument.value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                tool.output?.let { output ->
                    ToolOutput(output)
                }
            }
        }
    }
}

/**
 * What a tool answered, on a raised surface like a code block — it is machine output, not prose.
 *
 * Capped, and it says so when it caps: the median answer is 760 characters but the measured maximum
 * is 51 425, and a fold that pastes fifty thousand characters into the transcript has un-folded the
 * turn by another route.
 */
@Composable
private fun ToolOutput(output: String) {
    val shown = output.take(TOOL_OUTPUT_LIMIT)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = shown,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (output.length > TOOL_OUTPUT_LIMIT) {
            Text(
                text = stringResource(
                    Res.string.tasks_chat_output_truncated,
                    output.length - TOOL_OUTPUT_LIMIT,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val TOOL_OUTPUT_LIMIT = 2_000

/**
 * The composer, wearing the chat's clothes.
 *
 * Shape, fill, border and keyboard behaviour come from `:core:ui`'s [ChatInputDefaults] — the same
 * object the chat's own composer reads — so the two controls cannot drift apart. Above the box sits
 * the chips row the chat has: the connectors this session carries, and the model the next message
 * runs on. Both write straight through to the engine (`PATCH /session/{id}` for the rules,
 * `model` on the message for the call), so they are controls and not decoration.
 *
 * What is *not* shared is the chat's composer itself: attachments, MCP pickers, voice, queueing and
 * steering are chat concepts a mission session has none of, and a feature module cannot see
 * another's code anyway. Sharing the vocabulary is what is genuinely common.
 */
@Composable
private fun MissionChatInput(
    state: MissionChatUiState,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
    onToggleConnector: (String) -> Unit,
    onSelectModel: (EngineSelectableModel?) -> Unit,
    onRetryCatalogue: () -> Unit,
    onAddAttachments: (List<StagedAttachment>) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onTranscribeAudio: (ByteArray, String) -> Unit,
    onDismissTranscriptionError: () -> Unit,
) {
    var picker by remember { mutableStateOf(Picker.NONE) }

    Surface(tonalElevation = 2.dp) {
        // The keyboard, then the navigation bar — whichever is taller, never both stacked.
        //
        // `navigationBarsPadding()` alone left the composer *behind* the keyboard: it is the
        // Scaffold's bottomBar, so nothing lifts it on its own, and the nav-bar inset says nothing
        // about the IME. Reported 30/08/2026 — the box was unreachable the moment it was tapped.
        // Adding `imePadding()` on top would stack the two and leave a nav-bar-high gap under the
        // keyboard; `union` takes the larger, which is what « above whatever is at the bottom of
        // the screen » actually means. The chat reaches the same place differently — its composer
        // is an overlay inside the body, under a Scaffold that carries `imePadding()` itself.
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
            if (state.transcriptionFailed) {
                Text(
                    text = stringResource(Res.string.tasks_transcription_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismissTranscriptionError)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            state.sendError?.let { kind ->
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

            ComposerChips(
                state = state,
                onOpenConnectors = { picker = Picker.CONNECTORS },
                onOpenModels = { picker = Picker.MODELS },
                onRetryCatalogue = onRetryCatalogue,
            )

            if (state.attachments.isNotEmpty()) {
                StagedAttachmentsRow(state.attachments, onRemoveAttachment)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Null where the platform has no picker to offer (iOS today) — then no button,
                // rather than a button that does nothing.
                val openPicker = rememberMissionAttachmentPicker(onPick = onAddAttachments)
                if (openPicker != null) {
                    IconButton(onClick = openPicker) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = stringResource(Res.string.tasks_attach_photo),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // An audio file becomes words in the box, via the server's Whisper — no model on
                // the gateway hears audio, and the words are what the mission can actually read.
                val openAudio = rememberMissionAudioPicker(onPick = { onTranscribeAudio(it.bytes, it.mime) })
                if (openAudio != null) {
                    if (state.transcribing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(12.dp).size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = openAudio) {
                            Icon(
                                Icons.Outlined.Mic,
                                contentDescription = stringResource(Res.string.tasks_attach_audio),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(Res.string.tasks_chat_hint)) },
                    shape = ChatInputDefaults.shape,
                    colors = ChatInputDefaults.textFieldColors(),
                    keyboardOptions = ChatInputDefaults.keyboardOptions,
                    maxLines = MAX_INPUT_LINES,
                )
                MissionSendButton(
                    // `sending` counts as running: the gap between the POST and the answer's first
                    // token is exactly when someone wants to be able to call it off.
                    running = state.chat.streaming || state.sending,
                    // A photo can be the whole message.
                    canSend = state.input.isNotBlank() || state.attachments.isNotEmpty(),
                    onSend = onSend,
                    onStop = onStop,
                )
            }
        }
    }

    when (picker) {
        Picker.CONNECTORS -> ConnectorPickerSheet(
            options = state.connectors,
            ticked = state.enabledConnectors.orEmpty(),
            onToggle = onToggleConnector,
            onDismiss = { picker = Picker.NONE },
        )
        Picker.MODELS -> ModelPickerSheet(
            models = state.models,
            selected = state.effectiveModel,
            onSelect = {
                onSelectModel(it)
                picker = Picker.NONE
            },
            onDismiss = { picker = Picker.NONE },
        )
        Picker.NONE -> Unit
    }
}

private enum class Picker { NONE, CONNECTORS, MODELS }

/**
 * The row above the box: what this session can reach, and what it answers on.
 *
 * Both chips carry their current value in the label rather than opening onto it — « 3 connecteurs »
 * and the model's name — because the answer to « what is this mission allowed to do » should not
 * require opening a sheet to find out.
 *
 * The two are independent: the connectors come from the scheduler and the models from the engine,
 * so one host being unreachable leaves the other's chip standing. A single row that vanished
 * whenever either failed is what hid a working model picker behind a scheduler that was merely not
 * redeployed yet (30/08/2026).
 */
@Composable
private fun ComposerChips(
    state: MissionChatUiState,
    onOpenConnectors: () -> Unit,
    onOpenModels: () -> Unit,
    onRetryCatalogue: () -> Unit,
) {
    val connectorsFailed = state.connectorsError != null
    val modelsFailed = state.modelsError != null
    if (state.connectors.isEmpty() && state.models.isEmpty() && !connectorsFailed && !modelsFailed) return

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            // Naming what is missing beats « something did not load »: the two have different
            // causes and different fixes, and only one of them is ever the engine.
            connectorsFailed -> RetryChip(
                label = stringResource(Res.string.tasks_chat_connectors_failed),
                onClick = onRetryCatalogue,
            )
            state.connectors.isNotEmpty() -> AssistChip(
                onClick = onOpenConnectors,
                leadingIcon = { Icon(Icons.Outlined.Build, null, Modifier.size(16.dp)) },
                label = {
                    val granted = state.enabledConnectors
                    Text(
                        when {
                            // Not read back yet. « No connector » here was a claim the screen had no
                            // grounds for, and it was wrong on every mission the scheduler launched.
                            granted == null -> stringResource(Res.string.tasks_connectors)
                            granted.isEmpty() -> stringResource(Res.string.tasks_chat_no_connector)
                            else -> stringResource(
                                Res.string.tasks_chat_connector_count,
                                granted.size,
                            )
                        },
                    )
                },
            )
        }

        when {
            modelsFailed -> RetryChip(
                label = stringResource(Res.string.tasks_chat_models_failed),
                onClick = onRetryCatalogue,
            )
            state.models.isNotEmpty() -> AssistChip(
                onClick = onOpenModels,
                leadingIcon = { Icon(Icons.Outlined.Bolt, null, Modifier.size(16.dp)) },
                label = {
                    Text(state.effectiveModel?.label ?: stringResource(Res.string.tasks_model_default_short))
                },
            )
        }
    }
}

/** A chip that says what is missing and offers to go and get it again. */
@Composable
private fun RetryChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        leadingIcon = {
            Icon(
                Icons.Outlined.Refresh,
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    )
}

/**
 * Send, or stop what is running — the chat's button, down to the 56 dp target and the error-coloured
 * stop. Animated across the swap for the same reason the chat animates it: the two states occupy the
 * same spot, and a hard cut reads as the button having been replaced rather than having changed.
 */
@Composable
private fun MissionSendButton(
    running: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    AnimatedContent(
        targetState = running,
        transitionSpec = { (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut()) },
        label = "mission_send_stop_toggle",
    ) { showStop ->
        if (showStop) {
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

private const val MAX_INPUT_LINES = 6

/** The chat's 56 dp touch target, so the two composers line up when you switch between them. */
private val SEND_BUTTON_SIZE = 56.dp
private val SEND_ICON_SIZE = 28.dp

/** A pause long enough to mean « stopped here », short enough to survive a quick exit. */
private const val POSITION_SETTLE_MS = 400L

/**
 * One attachment in the transcript. An image draws as the picture — bounded, clipped like a bubble;
 * anything else names itself, because rendering raw base64 helps nobody.
 */
@Composable
private fun AttachmentContent(part: ChatPart.Attachment) {
    if (part.mime.startsWith("image/")) {
        AsyncImage(
            model = part.dataUrl,
            contentDescription = part.filename ?: stringResource(Res.string.tasks_attached_photo),
            modifier = Modifier
                .heightIn(max = ATTACHMENT_MAX_HEIGHT)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Text(
            part.filename ?: part.mime,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The photos staged for the next message: thumbnails, each with its remove cross. */
@Composable
private fun StagedAttachmentsRow(
    attachments: List<StagedAttachment>,
    onRemove: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { staged ->
            Box {
                AsyncImage(
                    model = staged.bytes,
                    contentDescription = staged.filename,
                    modifier = Modifier
                        .size(STAGED_THUMBNAIL_SIZE)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                IconButton(
                    onClick = { onRemove(staged.id) },
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.tasks_attachment_remove),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private val ATTACHMENT_MAX_HEIGHT = 280.dp
private val STAGED_THUMBNAIL_SIZE = 72.dp
