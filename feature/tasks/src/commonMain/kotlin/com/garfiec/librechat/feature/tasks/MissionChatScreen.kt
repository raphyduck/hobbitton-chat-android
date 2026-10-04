package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.datastore.MissionReadingPosition
import com.garfiec.librechat.core.data.engine.EngineProfile
import com.garfiec.librechat.feature.tasks.components.Explanation
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.chat_empty
import com.garfiec.librechat.feature.tasks.resources.chat_new
import com.garfiec.librechat.feature.tasks.resources.chat_title
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_back
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_empty
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_title
import com.garfiec.librechat.feature.tasks.resources.tasks_new
import com.garfiec.librechat.feature.tasks.resources.tasks_new_empty
import com.garfiec.librechat.feature.tasks.resources.tasks_open_drawer
import com.garfiec.librechat.feature.tasks.resources.tasks_prompt_calendar
import com.garfiec.librechat.feature.tasks.resources.tasks_prompt_document
import com.garfiec.librechat.feature.tasks.resources.tasks_prompt_emails
import com.garfiec.librechat.feature.tasks.resources.tasks_retry
import com.garfiec.librechat.feature.tasks.util.ChatPart
import com.garfiec.librechat.feature.tasks.util.ChatTurn
import com.garfiec.librechat.feature.tasks.util.MissionChatState
import com.garfiec.librechat.feature.tasks.util.hint
import com.garfiec.librechat.feature.tasks.util.mergedAssistantRuns
import com.garfiec.librechat.feature.tasks.util.title
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One engine session, as a conversation — a mission's, or since D-077 a chat's. The transcript is
 * replayed on open and the reply streams in token by token; the box at the bottom talks back to the
 * same session.
 *
 * [sessionId] is null for a chat that does not exist yet: the first send creates it on the chat
 * profile, and [onChatStart] hands the new session to the navigation, which opens it in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionChatScreen(
    sessionId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    /** Non-null when opened from the drawer: the bar then carries the menu, as a chat does. */
    onOpenDrawer: (() -> Unit)? = null,
    profile: EngineProfile = EngineProfile.TASK,
    onChatStart: (sessionId: String, title: String) -> Unit = { _, _ -> },
    /** Non-null on a chat already under way: the bar offers a fresh one. */
    onNewChat: (() -> Unit)? = null,
    viewModel: MissionChatViewModel = koinViewModel { parametersOf(MissionChatArgs(sessionId, profile)) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val currentOnChatStart by rememberUpdatedState(onChatStart)
    LaunchedEffect(state.started) {
        state.started?.let { started -> currentOnChatStart(started.sessionId, started.title) }
    }

    // Le cas dominant : le téléphone dort, le flux tombe, la mission continue de parler. Comme le
    // flux classique reprend à « maintenant » et ne rejoue rien, revenir au premier plan est le
    // moment où l'écran est le plus sûrement en retard — et celui où personne ne pense à tirer.
    // La PREMIÈRE reprise est sautée : elle suit l'ouverture, que le ViewModel a déjà servie. Sans
    // ce garde-fou chaque ouverture d'écran paierait deux fois le transcript pour le même résultat.
    var dejaRepris by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onVisible()
        if (dejaRepris) viewModel.refresh() else dejaRepris = true
    }
    // Hors de vue : une question posée ici sonne alors par une notification, pas par un simple son.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onHidden() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title.ifBlank { stringResource(defaultTitle(profile, sessionId)) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
                actions = {
                    if (onNewChat != null) {
                        IconButton(onClick = onNewChat) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(Res.string.chat_new),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            // The agent is waiting on an answer: the form takes the composer's place until it has one.
            val question = state.pendingQuestion
            if (question != null) {
                MissionQuestionForm(
                    request = question,
                    draft = state.questionDraftFor(question),
                    sending = state.answeringQuestion,
                    error = state.questionError,
                    onPick = viewModel::pickQuestionOption,
                    onType = viewModel::typeQuestionAnswer,
                    onSend = viewModel::answerQuestion,
                    onDismiss = viewModel::dismissQuestion,
                )
            } else {
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
            }
        },
    ) { padding ->
        MissionChatBody(
            state = state,
            contentPadding = padding,
            onRetryHistory = viewModel::retryHistory,
            onRefresh = viewModel::refresh,
            onRememberPosition = viewModel::rememberPosition,
            onPickPrompt = viewModel::onInputChange,
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
    onPickPrompt: (String) -> Unit,
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
                state.loadingHistory && state.chat.turns.isEmpty() -> TranscriptSkeleton()

                historyFailure != null && state.chat.turns.isEmpty() -> Explanation(
                    title = stringResource(historyFailure.title()),
                    hint = historyFailure.hint()?.let { stringResource(it) },
                    action = stringResource(Res.string.tasks_retry) to onRetryHistory,
                )

                state.chat.turns.isEmpty() -> EmptyConversation(
                    text = stringResource(
                        when {
                            state.profile == EngineProfile.CHAT -> Res.string.chat_empty
                            // A task not started yet: nothing to talk to, only something to describe.
                            state.isNew -> Res.string.tasks_new_empty
                            else -> Res.string.tasks_chat_empty
                        },
                    ),
                    // Examples only where a first message is still to be written: a task that already
                    // ran has its own subject, and a generic prompt there would be noise.
                    onPickPrompt = if (state.profile == EngineProfile.CHAT || state.isNew) onPickPrompt else null,
                    modifier = Modifier.align(Alignment.Center),
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
    // One drawn turn per run of assistant steps: their tool calls fold into one growing line.
    val turns = remember(chat.turns) { chat.turns.mergedAssistantRuns() }
    // Open where the reader left off. Once — hence the flag, saved across rotation: a second
    // restore would yank the list back out from under someone who has since scrolled.
    //
    // It waits on BOTH the stored position having been read and the transcript having landed,
    // because scrolling to item 40 of an empty list is a no-op the follow effect below then
    // finishes by dropping to the tail. Without a saved position the tail IS the right place, and
    // that is what this screen did for everyone before 31/08/2026.
    var restored by rememberSaveable(turns.isNotEmpty()) { mutableStateOf(false) }
    LaunchedEffect(positionKnown, turns.isNotEmpty()) {
        if (restored || !positionKnown || turns.isEmpty()) return@LaunchedEffect
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
    LaunchedEffect(turns.size, tailLength(chat), restored) {
        if (!restored) return@LaunchedEffect
        val info = listState.layoutInfo
        val nearTail = info.visibleItemsInfo.lastOrNull()
            ?.let { it.index >= info.totalItemsCount - 2 } ?: true
        if (nearTail) listState.animateScrollToItem((turns.size - 1).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // The chat's rhythm: 16 dp gutters, 16 dp between messages (its 2 x 8 dp bubble padding).
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            count = turns.size,
            // Keyed by message id: a delta rewrites the last turn on every token, and without a key
            // Compose reuses by position and re-composes every bubble below it.
            key = { index -> turns[index].key },
            contentType = { index -> turns[index]::class },
        ) { index ->
            val turn = turns[index]
            val live = chat.streaming && index == turns.lastIndex
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

/**
 * The empty conversation: one sentence, then a few example prompts. A tap puts the prompt in the
 * composer — never sends it — so it can be read and edited first.
 */
@Composable
private fun EmptyConversation(
    text: String,
    onPickPrompt: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onPickPrompt != null) {
            Spacer(Modifier.height(8.dp))
            EXAMPLE_PROMPTS.forEach { prompt ->
                val label = stringResource(prompt)
                SuggestionChip(
                    onClick = { onPickPrompt(label) },
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

/**
 * Stand-in bubbles while the transcript loads: the shape of a conversation, so the screen does not
 * read as empty, and no shimmer — plain blocks on the raised surface colour.
 */
@Composable
private fun TranscriptSkeleton() {
    val fill = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(SKELETON_EXCHANGES) {
            Box(
                Modifier
                    .align(Alignment.End)
                    .fillMaxWidth(SKELETON_USER_WIDTH)
                    .height(SKELETON_BUBBLE_HEIGHT)
                    .background(fill, RoundedCornerShape(16.dp)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SKELETON_LINE_WIDTHS.forEach { width ->
                    Box(
                        Modifier
                            .fillMaxWidth(width)
                            .height(SKELETON_LINE_HEIGHT)
                            .background(fill, RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}

/** What the bar says when the session has no title of its own. */
private fun defaultTitle(profile: EngineProfile, sessionId: String?) = when {
    profile == EngineProfile.TASK && sessionId == null -> Res.string.tasks_new
    profile == EngineProfile.TASK -> Res.string.tasks_chat_title
    sessionId == null -> Res.string.chat_new
    else -> Res.string.chat_title
}

private fun tailLength(chat: MissionChatState): Int {
    val last = chat.turns.lastOrNull() ?: return 0
    val parts = when (last) {
        is ChatTurn.User -> last.parts
        is ChatTurn.Assistant -> last.parts
    }
    return parts.sumOf { part -> if (part is ChatPart.Text) part.text.length else 1 }
}

/** A pause long enough to mean « stopped here », short enough to survive a quick exit. */
private const val POSITION_SETTLE_MS = 400L

private val EXAMPLE_PROMPTS = listOf(
    Res.string.tasks_prompt_emails,
    Res.string.tasks_prompt_calendar,
    Res.string.tasks_prompt_document,
)

private const val SKELETON_EXCHANGES = 2
private const val SKELETON_USER_WIDTH = 0.55f
private val SKELETON_LINE_WIDTHS = listOf(0.9f, 0.75f, 0.5f)
private val SKELETON_BUBBLE_HEIGHT = 40.dp
private val SKELETON_LINE_HEIGHT = 14.dp
