package com.garfiec.librechat.feature.tasks

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.engine.ConnectorOption
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.ui.input.ChatInputDefaults
import com.garfiec.librechat.feature.tasks.components.MissionMarkdown
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_back
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_collapse
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_connector_count
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_connectors_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_empty
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_expand
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_models_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_no_connector
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_reasoning
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_send
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_title
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_tool_count
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_working
import com.garfiec.librechat.feature.tasks.resources.tasks_connectors
import com.garfiec.librechat.feature.tasks.resources.tasks_model
import com.garfiec.librechat.feature.tasks.resources.tasks_model_default_short
import com.garfiec.librechat.feature.tasks.resources.tasks_retry
import com.garfiec.librechat.feature.tasks.resources.tasks_stop
import com.garfiec.librechat.feature.tasks.util.ChatBlock
import com.garfiec.librechat.feature.tasks.util.ChatPart
import com.garfiec.librechat.feature.tasks.util.ChatTurn
import com.garfiec.librechat.feature.tasks.util.MissionChatState
import com.garfiec.librechat.feature.tasks.util.ToolState
import com.garfiec.librechat.feature.tasks.util.asBlocks
import com.garfiec.librechat.feature.tasks.util.hasFailure
import com.garfiec.librechat.feature.tasks.util.hasReasoning
import com.garfiec.librechat.feature.tasks.util.isRunning
import com.garfiec.librechat.feature.tasks.util.toolCount
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
private fun AssistantTurn(turn: ChatTurn.Assistant, streaming: Boolean) {
    val blocks = remember(turn.parts) { turn.parts.asBlocks() }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is ChatBlock.Prose -> MissionMarkdown(block.part.text)
                is ChatBlock.Activity -> ActivityBlock(block)
            }
        }
        val silent = blocks.none { it is ChatBlock.Prose }
        if (streaming && silent) {
            Text(
                text = stringResource(Res.string.tasks_chat_working),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MUTED_ALPHA),
            )
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
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                running -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                failed -> Icon(
                    Icons.Filled.Close,
                    null,
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                else -> Icon(
                    Icons.Outlined.Build,
                    null,
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MUTED_ALPHA),
                )
            }
            Text(
                text = activityLabel(block),
                style = MaterialTheme.typography.labelMedium,
                color = if (failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) Res.string.tasks_chat_collapse else Res.string.tasks_chat_expand,
                ),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(
                Modifier.padding(start = 20.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                block.parts.forEach { part ->
                    when (part) {
                        is ChatPart.Tool -> ToolRow(part)
                        is ChatPart.Reasoning -> Text(
                            text = part.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MUTED_ALPHA),
                        )
                        is ChatPart.Text -> Unit
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

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                    canSend = state.input.isNotBlank(),
                    onSend = onSend,
                    onStop = onStop,
                )
            }
        }
    }

    when (picker) {
        Picker.CONNECTORS -> ConnectorSheet(
            options = state.connectors,
            enabled = state.enabledConnectors,
            onToggle = onToggleConnector,
            onDismiss = { picker = Picker.NONE },
        )
        Picker.MODELS -> ModelSheet(
            models = state.models,
            selected = state.model,
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
                    Text(
                        if (state.enabledConnectors.isEmpty()) {
                            stringResource(Res.string.tasks_chat_no_connector)
                        } else {
                            stringResource(
                                Res.string.tasks_chat_connector_count,
                                state.enabledConnectors.size,
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
                    Text(state.model?.label ?: stringResource(Res.string.tasks_model_default_short))
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
 * Ticking a connector re-grants the **live** session — it does not wait for the next launch.
 *
 * The tool count is shown because it is what a connector costs: every tool a session declares is
 * re-sent to the model on every turn, and the platform has measured a mission spend the bulk of its
 * budget on a catalogue it never called (server-side D-040). « imap, 10 outils » is that price, in
 * the one place where someone is choosing to pay it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectorSheet(
    options: List<ConnectorOption>,
    enabled: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            Text(
                stringResource(Res.string.tasks_connectors),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = option.enabled) { onToggle(option.name) }
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = option.name in enabled,
                        enabled = option.enabled,
                        onCheckedChange = { onToggle(option.name) },
                    )
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(option.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(Res.string.tasks_chat_tool_count, option.toolCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Which model the next message runs on. Per message, which is how the engine takes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(
    models: List<EngineSelectableModel>,
    selected: EngineSelectableModel?,
    onSelect: (EngineSelectableModel?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            Text(
                stringResource(Res.string.tasks_model),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            // « Whatever the session already runs on » stays reachable: an unpicked model is not a
            // missing setting, it is the engine's own choice, and taking it back should be possible.
            ModelRow(
                label = stringResource(Res.string.tasks_model_default_short),
                selected = selected == null,
                onClick = { onSelect(null) },
            )
            models.forEach { candidate ->
                ModelRow(
                    label = candidate.label,
                    selected = candidate == selected,
                    onClick = { onSelect(candidate) },
                )
            }
        }
    }
}

@Composable
private fun ModelRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
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

private const val BUBBLE_MAX_FRACTION = 0.85f
private const val MUTED_ALPHA = 0.7f
private const val MAX_INPUT_LINES = 6

/** The chat's 56 dp touch target, so the two composers line up when you switch between them. */
private val SEND_BUTTON_SIZE = 56.dp
private val SEND_ICON_SIZE = 28.dp
