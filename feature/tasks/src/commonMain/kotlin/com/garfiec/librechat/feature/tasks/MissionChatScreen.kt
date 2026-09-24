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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.garfiec.librechat.core.data.datastore.MissionReadingPosition
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.ui.input.ChatInputDefaults
import com.garfiec.librechat.core.ui.markdown.StreamingWaitIndicator
import com.garfiec.librechat.core.ui.util.copyToClipboard
import com.garfiec.librechat.feature.tasks.components.ConnectorPickerSheet
import com.garfiec.librechat.feature.tasks.components.DisclosureRow
import com.garfiec.librechat.feature.tasks.components.Explanation
import com.garfiec.librechat.feature.tasks.components.MissionMarkdown
import com.garfiec.librechat.feature.tasks.components.ModelPickerSheet
import com.garfiec.librechat.feature.tasks.components.rememberMissionAttachmentPicker
import com.garfiec.librechat.feature.tasks.components.rememberMissionAudioPicker
import com.garfiec.librechat.feature.tasks.components.rememberMissionDictation
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_attach_audio
import com.garfiec.librechat.feature.tasks.resources.tasks_attach_photo
import com.garfiec.librechat.feature.tasks.resources.tasks_attached_photo
import com.garfiec.librechat.feature.tasks.resources.tasks_attachment_remove
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_add
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_back
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_collapse
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_connector_count
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_connectors_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_copy
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
import com.garfiec.librechat.feature.tasks.resources.tasks_copied
import com.garfiec.librechat.feature.tasks.resources.tasks_dictate
import com.garfiec.librechat.feature.tasks.resources.tasks_dictate_stop
import com.garfiec.librechat.feature.tasks.resources.tasks_model_default_short
import com.garfiec.librechat.feature.tasks.resources.tasks_open_drawer
import com.garfiec.librechat.feature.tasks.resources.tasks_retry
import com.garfiec.librechat.feature.tasks.resources.tasks_stop
import com.garfiec.librechat.feature.tasks.resources.tasks_transcription_failed
import com.garfiec.librechat.feature.tasks.util.AudioNote
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
    /** Non-null when opened from the drawer: the bar then carries the menu, as a chat does. */
    onOpenDrawer: (() -> Unit)? = null,
    viewModel: MissionChatViewModel = koinViewModel { parametersOf(sessionId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Le cas dominant : le téléphone dort, le flux tombe, la mission continue de parler. Comme le
    // flux classique reprend à « maintenant » et ne rejoue rien, revenir au premier plan est le
    // moment où l'écran est le plus sûrement en retard — et celui où personne ne pense à tirer.
    // La PREMIÈRE reprise est sautée : elle suit l'ouverture, que le ViewModel a déjà servie. Sans
    // ce garde-fou chaque ouverture d'écran paierait deux fois le transcript pour le même résultat.
    var dejaRepris by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (dejaRepris) viewModel.refresh() else dejaRepris = true
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { stringResource(Res.string.tasks_chat_title) }) },
                navigationIcon = {
                    if (onOpenDrawer != null) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(Res.string.tasks_open_drawer),
                            )
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.tasks_chat_back),
                            )
                        }
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
                onAttachAudio = viewModel::attachAudio,
                onRemoveAudioNote = viewModel::removeAudioNote,
                onDismissTranscriptionError = viewModel::dismissTranscriptionError,
            )
        },
    ) { padding ->
        MissionChatBody(
            state = state,
            contentPadding = padding,
            onRetryHistory = viewModel::retryHistory,
            onRefresh = viewModel::refresh,
            onRememberPosition = viewModel::rememberPosition,
        )
    }
}

@Composable
private fun MissionChatBody(
    state: MissionChatUiState,
    contentPadding: PaddingValues,
    onRetryHistory: () -> Unit,
    onRefresh: () -> Unit,
    onRememberPosition: (index: Int, offset: Int) -> Unit,
) {
    // Le geste que Raphaël a cherché le 21/09/2026 avant de contourner par la liste. Il ne masque
    // pas le défaut — le flux reprend toujours à « maintenant », voir MissionChatViewModel.refresh —
    // il rend seulement le rattrapage possible sans quitter l'écran.
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.padding(contentPadding).fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
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
        // Under a finished answer, the action row the chat and Claude both have. Only « copy » for
        // now: a mission has no regenerate, and its feedback would reach nobody.
        if (!streaming && lastProse >= 0) {
            val prose = blocks.filterIsInstance<ChatBlock.Prose>().joinToString("\n\n") { it.part.text }
            CopyTurnButton(prose)
        }
    }
}

/** Copies an answer's prose — without its folded work — and says so for two seconds. */
@Composable
private fun CopyTurnButton(text: String) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }
    // Outside the turn's selection: a button is not text anyone meant to select.
    DisableSelection {
        IconButton(
            onClick = {
                copyToClipboard(text.trim(), "Mission")
                copied = true
            },
            modifier = Modifier.size(COPY_BUTTON_SIZE),
        ) {
            Icon(
                if (copied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                contentDescription = stringResource(if (copied) Res.string.tasks_copied else Res.string.tasks_chat_copy),
                modifier = Modifier.size(18.dp),
                tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val COPIED_FEEDBACK_MS = 2_000L
private val COPY_BUTTON_SIZE = 36.dp

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
 * The composer, laid out as Claude's (capture of 24/09/2026): one rounded box, the text on top, and
 * under it a single row — « + » for what can be attached, the model and the connectors as pills,
 * then the mic and send at the far end.
 *
 * It replaced a chips row floating above the box and a line of four icons beside it (photo, audio
 * file, mic, text, send), which left the text field a third of a phone's width. Everything is still
 * one tap from the box; the two attachment kinds, used far less than the rest, moved behind the
 * « + » — the same trade the chat made with its tools in lot 3.
 *
 * Shape, fill and border still come from `:core:ui`'s [ChatInputDefaults], so this box and the chat's
 * cannot drift apart in the colours they share. The model and connectors write straight through to
 * the engine (`model` on the message, `PATCH /session/{id}` for the rules): controls, not decoration.
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
    onAttachAudio: (bytes: ByteArray, mime: String, filename: String) -> Unit,
    onRemoveAudioNote: (String) -> Unit,
    onDismissTranscriptionError: () -> Unit,
) {
    var picker by remember { mutableStateOf(Picker.NONE) }
    // Null where the platform has nothing to offer (iOS today) — then no menu entry, rather than an
    // entry that does nothing. Called here, unconditionally: they remember launchers.
    val openPhoto = rememberMissionAttachmentPicker(onPick = onAddAttachments)
    // A deposited audio file goes to the THREAD: transcribed on pick, staged as a quoted note, sent
    // with the message. Whisper because no model on the gateway hears audio.
    val openAudio = rememberMissionAudioPicker(onPick = { onAttachAudio(it.bytes, it.mime, it.filename) })
    // The mic DICTATES: tap to record, tap to stop, and the words land in the box — where the
    // speaker reads what Whisper heard before it becomes an instruction (asked for on 31/08/2026).
    val dictation = rememberMissionDictation(onCapture = { onTranscribeAudio(it.bytes, it.mime) })

    Surface {
        // The keyboard, then the navigation bar — whichever is taller, never both stacked.
        //
        // `navigationBarsPadding()` alone left the composer *behind* the keyboard: it is the
        // Scaffold's bottomBar, so nothing lifts it on its own (reported 30/08/2026). Adding
        // `imePadding()` on top would stack the two; `union` takes the larger.
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
            if (state.transcriptionFailed) {
                ComposerNotice(stringResource(Res.string.tasks_transcription_failed), onDismissTranscriptionError)
            }
            state.sendError?.let { kind ->
                // The send failed and the text was put back — say why, once, dismissible on tap.
                ComposerNotice(stringResource(kind.title()), onDismissError)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(ChatInputDefaults.shape)
                    .background(ChatInputDefaults.containerColor)
                    .border(1.dp, ChatInputDefaults.borderColor, ChatInputDefaults.shape)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (state.attachments.isNotEmpty() || state.audioNotes.isNotEmpty()) {
                    StagedAttachmentsRow(state.attachments, state.audioNotes, onRemoveAttachment, onRemoveAudioNote)
                }
                TextField(
                    value = state.input,
                    onValueChange = onInput,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(Res.string.tasks_chat_hint)) },
                    // The box draws the frame; the field inside it draws nothing of its own.
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = ChatInputDefaults.keyboardOptions,
                    maxLines = MAX_INPUT_LINES,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AddButton(openPhoto = openPhoto, openAudio = openAudio, audioEnabled = !state.transcribing)
                    // The pills scroll among themselves, so a long model name never pushes send
                    // off the row.
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ComposerChips(
                            state = state,
                            onOpenConnectors = { picker = Picker.CONNECTORS },
                            onOpenModels = { picker = Picker.MODELS },
                            onRetryCatalogue = onRetryCatalogue,
                        )
                    }
                    if (dictation != null) {
                        if (state.transcribing) {
                            Box(Modifier.size(COMPOSER_BUTTON_SIZE), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            IconButton(onClick = dictation.toggle, modifier = Modifier.size(COMPOSER_BUTTON_SIZE)) {
                                if (dictation.recording) {
                                    Icon(
                                        Icons.Filled.Stop,
                                        contentDescription = stringResource(Res.string.tasks_dictate_stop),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.Mic,
                                        contentDescription = stringResource(Res.string.tasks_dictate),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    MissionSendButton(
                        // `sending` counts as running: the gap between the POST and the answer's
                        // first token is exactly when someone wants to be able to call it off.
                        running = state.chat.streaming || state.sending,
                        // A photo — or a transcribed audio — can be the whole message.
                        canSend = state.input.isNotBlank() ||
                            state.attachments.isNotEmpty() ||
                            state.audioNotes.isNotEmpty(),
                        onSend = onSend,
                        onStop = onStop,
                    )
                }
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
            prices = state.prices,
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

/** One line above the box that says what went wrong, dismissed by a tap on it. */
@Composable
private fun ComposerNotice(text: String, onDismiss: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * The « + »: what can be attached to the next message. Absent when the platform offers neither
 * picker — a button that opens an empty menu is worse than no button.
 */
@Composable
private fun AddButton(openPhoto: (() -> Unit)?, openAudio: (() -> Unit)?, audioEnabled: Boolean) {
    if (openPhoto == null && openAudio == null) return
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconButton(onClick = { open = true }, modifier = Modifier.size(COMPOSER_BUTTON_SIZE)) {
            Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.tasks_chat_add))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            openPhoto?.let { pick ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.tasks_attach_photo)) },
                    leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, null) },
                    onClick = {
                        open = false
                        pick()
                    },
                )
            }
            openAudio?.let { pick ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.tasks_attach_audio)) },
                    leadingIcon = { Icon(Icons.Outlined.AudioFile, null) },
                    enabled = audioEnabled,
                    onClick = {
                        open = false
                        pick()
                    },
                )
            }
        }
    }
}

/**
 * The pills under the text: the model the next message runs on, then what this session can reach.
 *
 * Both carry their current value — the model's name, « 3 connecteurs » — because the answer to
 * « what is this mission allowed to do » should not require opening a sheet. The model leads, as
 * on Claude's composer.
 *
 * The two are independent: the connectors come from the scheduler and the models from the engine,
 * so one host being unreachable leaves the other's pill standing. A single row that vanished
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
    when {
        // Naming what is missing beats « something did not load »: the two have different causes
        // and different fixes, and only one of them is ever the engine.
        state.modelsError != null -> ComposerPill(
            label = stringResource(Res.string.tasks_chat_models_failed),
            icon = Icons.Outlined.Refresh,
            onClick = onRetryCatalogue,
        )
        state.models.isNotEmpty() -> ComposerPill(
            label = state.effectiveModel?.label ?: stringResource(Res.string.tasks_model_default_short),
            onClick = onOpenModels,
        )
    }
    when {
        state.connectorsError != null -> ComposerPill(
            label = stringResource(Res.string.tasks_chat_connectors_failed),
            icon = Icons.Outlined.Refresh,
            onClick = onRetryCatalogue,
        )
        state.connectors.isNotEmpty() -> {
            val granted = state.enabledConnectors
            ComposerPill(
                label = when {
                    // Not read back yet. « No connector » here was a claim the screen had no
                    // grounds for, and it was wrong on every mission the scheduler launched.
                    granted == null -> stringResource(Res.string.tasks_connectors)
                    granted.isEmpty() -> stringResource(Res.string.tasks_chat_no_connector)
                    else -> stringResource(Res.string.tasks_chat_connector_count, granted.size)
                },
                icon = Icons.Outlined.Build,
                onClick = onOpenConnectors,
            )
        }
    }
}

/** A rounded, filled pill — the look of Claude's model and mode buttons. */
@Composable
private fun ComposerPill(label: String, onClick: () -> Unit, icon: ImageVector? = null) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.heightIn(min = COMPOSER_BUTTON_SIZE),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon?.let { Icon(it, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/**
 * Send, or stop what is running, in the same round spot at the end of the row. Animated across the
 * swap: the two states occupy one place, and a hard cut reads as the button having been replaced.
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
                modifier = Modifier.size(COMPOSER_BUTTON_SIZE),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(imageVector = Icons.Filled.Stop, contentDescription = stringResource(Res.string.tasks_stop))
            }
        } else {
            IconButton(
                onClick = onSend,
                modifier = Modifier.size(COMPOSER_BUTTON_SIZE),
                enabled = canSend,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = stringResource(Res.string.tasks_chat_send),
                )
            }
        }
    }
}

private const val MAX_INPUT_LINES = 6

/** Every round control in the composer's row: « + », pills, mic, send. 44 dp keeps it a thumb's. */
private val COMPOSER_BUTTON_SIZE = 44.dp

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

/**
 * What is staged for the next message: photo thumbnails, and one chip per transcribed audio —
 * each with its remove cross. The chip names the file, because that name is what the thread will
 * quote above the words.
 */
@Composable
private fun StagedAttachmentsRow(
    attachments: List<StagedAttachment>,
    audioNotes: List<AudioNote>,
    onRemoveAttachment: (String) -> Unit,
    onRemoveAudioNote: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
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
                    onClick = { onRemoveAttachment(staged.id) },
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
        audioNotes.forEach { note ->
            AssistChip(
                onClick = { onRemoveAudioNote(note.id) },
                leadingIcon = { Icon(Icons.Outlined.AudioFile, null, Modifier.size(16.dp)) },
                label = { Text(note.filename) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.tasks_attachment_remove),
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

private val ATTACHMENT_MAX_HEIGHT = 280.dp
private val STAGED_THUMBNAIL_SIZE = 72.dp
