package com.garfiec.librechat.feature.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.datastore.MissionReadingPosition
import com.garfiec.librechat.core.data.engine.EngineProfile
import com.garfiec.librechat.core.ui.input.ChatInputDefaults
import com.garfiec.librechat.feature.tasks.components.Explanation
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.chat_empty
import com.garfiec.librechat.feature.tasks.resources.chat_greeting_evening
import com.garfiec.librechat.feature.tasks.resources.chat_greeting_evening_anonymous
import com.garfiec.librechat.feature.tasks.resources.chat_greeting_morning
import com.garfiec.librechat.feature.tasks.resources.chat_greeting_morning_anonymous
import com.garfiec.librechat.feature.tasks.resources.chat_new
import com.garfiec.librechat.feature.tasks.resources.chat_title
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_back
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_empty
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_scroll_to_end
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
import com.garfiec.librechat.feature.tasks.util.currentHourOfDay
import com.garfiec.librechat.feature.tasks.util.hint
import com.garfiec.librechat.feature.tasks.util.mergedAssistantRuns
import com.garfiec.librechat.feature.tasks.util.title
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
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

    // A short click under the thumb on send and on stop: the two gestures that commit something.
    val haptics = LocalHapticFeedback.current
    val send = remember(viewModel) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            viewModel.send()
        }
    }
    val stop = remember(viewModel) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            viewModel.stop()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    // A conversation that has not started yet has nothing to be titled: the
                    // greeting below does the talking, as on Claude's own new chat.
                    if (sessionId != null || profile == EngineProfile.TASK) {
                        Text(
                            text = title.ifBlank { stringResource(defaultTitle(profile, sessionId)) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                    onSend = send,
                    onStop = stop,
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

/** What the body shows: the four states cross-fade rather than cut, so the first reply does not jump in. */
private enum class BodyKind { LOADING, FAILED, EMPTY, TURNS }

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
        val historyFailure = state.historyError
        val kind = when {
            // The transcript is the conversation's past; while it loads, an empty screen would be a
            // lie about a session that has been talking for hours.
            state.loadingHistory && state.chat.turns.isEmpty() -> BodyKind.LOADING
            historyFailure != null && state.chat.turns.isEmpty() -> BodyKind.FAILED
            state.chat.turns.isEmpty() -> BodyKind.EMPTY
            else -> BodyKind.TURNS
        }
        Crossfade(targetState = kind, animationSpec = tween(BODY_FADE_MS), label = "chat_body") { shown ->
            Box(Modifier.fillMaxSize()) {
                when (shown) {
                    BodyKind.LOADING -> TranscriptSkeleton()

                    BodyKind.FAILED -> Explanation(
                        title = stringResource(historyFailure?.title() ?: Res.string.tasks_retry),
                        hint = historyFailure?.hint()?.let { stringResource(it) },
                        action = stringResource(Res.string.tasks_retry) to onRetryHistory,
                    )

                    BodyKind.EMPTY -> EmptyConversation(
                        profile = state.profile,
                        isNew = state.isNew,
                        greetingName = state.greetingName,
                        // Examples only where a first message is still to be written: a task that
                        // already ran has its own subject, and a generic prompt there would be noise.
                        onPickPrompt = if (state.profile == EngineProfile.CHAT || state.isNew) onPickPrompt else null,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    BodyKind.TURNS -> MissionTurns(
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
        if (listState.isNearTail()) listState.animateScrollToItem((turns.size - 1).coerceAtLeast(0))
    }
    // Scrolled back up to read while the answer keeps coming: one round button brings the tail back.
    // Derived, so it only recomposes the button when the answer actually crosses the threshold.
    val awayFromTail by remember(listState) { derivedStateOf { !listState.isNearTail() } }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
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
                //
                // `animateItem`: a new message fades and settles into place instead of popping.
                Box(Modifier.animateItem()) {
                    SelectionContainer {
                        when (turn) {
                            is ChatTurn.User -> UserBubble(turn, fontScale)
                            is ChatTurn.Assistant -> AssistantTurn(turn, streaming = live, fontScale = fontScale)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = awayFromTail,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            Surface(
                onClick = { scope.launch { listState.animateScrollToItem((turns.size - 1).coerceAtLeast(0)) } },
                shape = CircleShape,
                color = ChatInputDefaults.containerColor,
                border = BorderStroke(1.dp, ChatInputDefaults.borderColor),
                shadowElevation = ChatInputDefaults.shadowElevation,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(Res.string.tasks_chat_scroll_to_end),
                    modifier = Modifier.padding(8.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** At the tail, or one item short of it; an undrawn list counts as at the tail. */
private fun LazyListState.isNearTail(): Boolean {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return true
    return last.index >= info.totalItemsCount - 2
}

/**
 * The empty conversation. A new chat greets the person by the time of day and, when the portal
 * gave one, by their first name, in the serif: it is the one screen seen on every opening, and the
 * place the app speaks first. Under it, one line and a few example prompts as quiet rows; a tap
 * puts the prompt in the composer — never sends it — so it can be read and edited first.
 */
@Composable
internal fun EmptyConversation(
    profile: EngineProfile,
    isNew: Boolean,
    greetingName: String?,
    onPickPrompt: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isChat = profile == EngineProfile.CHAT
    Column(
        modifier = modifier.widthIn(max = EMPTY_MAX_WIDTH).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isChat || isNew) {
            Text(
                text = if (isChat) greeting(greetingName) else stringResource(Res.string.tasks_new),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = stringResource(
                when {
                    isChat -> Res.string.chat_empty
                    // A task not started yet: nothing to talk to, only something to describe.
                    isNew -> Res.string.tasks_new_empty
                    else -> Res.string.tasks_chat_empty
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onPickPrompt != null) {
            Spacer(Modifier.height(28.dp))
            Column(Modifier.fillMaxWidth()) {
                EXAMPLE_PROMPTS.forEachIndexed { index, prompt ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PromptRow(label = stringResource(prompt), onPick = onPickPrompt)
                }
            }
        }
    }
}

/** One example, a row with a chevron: text, not a chip, so the list reads as suggestions and not as buttons. */
@Composable
private fun PromptRow(label: String, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, PromptRowShape)
            .clickable { onPick(label) }
            .heightIn(min = 44.dp)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** « Bonjour, Raphaël. » before six in the evening, « Bonsoir, Raphaël. » after; without the name when the portal gave none. */
@Composable
private fun greeting(name: String?): String {
    val hour = remember { currentHourOfDay() }
    val evening = hour >= EVENING_HOUR || hour < MORNING_HOUR
    return when {
        name == null && evening -> stringResource(Res.string.chat_greeting_evening_anonymous)
        name == null -> stringResource(Res.string.chat_greeting_morning_anonymous)
        evening -> stringResource(Res.string.chat_greeting_evening, name)
        else -> stringResource(Res.string.chat_greeting_morning, name)
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
private fun defaultTitle(profile: EngineProfile, sessionId: String?): StringResource = when {
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
private const val BODY_FADE_MS = 220

/** « Bonsoir » from six in the evening until five in the morning. */
private const val EVENING_HOUR = 18
private const val MORNING_HOUR = 5

private val EXAMPLE_PROMPTS = listOf(
    Res.string.tasks_prompt_emails,
    Res.string.tasks_prompt_calendar,
    Res.string.tasks_prompt_document,
)

private val EMPTY_MAX_WIDTH = 400.dp
private val PromptRowShape = RoundedCornerShape(10.dp)

private const val SKELETON_EXCHANGES = 2
private const val SKELETON_USER_WIDTH = 0.55f
private val SKELETON_LINE_WIDTHS = listOf(0.9f, 0.75f, 0.5f)
private val SKELETON_BUBBLE_HEIGHT = 40.dp
private val SKELETON_LINE_HEIGHT = 14.dp
