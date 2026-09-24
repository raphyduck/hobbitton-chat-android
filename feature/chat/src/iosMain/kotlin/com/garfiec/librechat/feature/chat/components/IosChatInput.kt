package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.TokenUsage
import com.garfiec.librechat.core.ui.input.ChatInputDefaults
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_attach_file
import com.garfiec.librechat.feature.chat.resources.cd_paste_image
import com.garfiec.librechat.feature.chat.viewmodel.ChatInputGates
import com.garfiec.librechat.feature.chat.viewmodel.DuringRunSendTarget
import com.garfiec.librechat.feature.chat.viewmodel.PendingSteerChip
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import org.jetbrains.compose.resources.stringResource

@Composable
fun IosChatInput(
    inputText: String,
    isStreaming: Boolean,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    /**
     * Opens the chat options sheet from the "+" button. The sheet is hosted by the chat screen,
     * not here — see `ChatOptionsSheetController`.
     */
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the model selector from the composer's pill; null hides the pill. */
    onOpenModelSelector: (() -> Unit)? = null,
    onQueue: () -> Unit = {},
    canQueue: Boolean = false,
    onDuringRunSend: () -> Unit = {},
    onSteer: () -> Unit = {},
    canSteer: Boolean = false,
    duringRunAction: DuringRunAction = DuringRunAction.QUEUE,
    duringRunSendTarget: DuringRunSendTarget = DuringRunSendTarget.QUEUE,
    pendingSteers: List<PendingSteerChip> = emptyList(),
    onCancelSteer: (steerId: String) -> Unit = {},
    onSetDuringRunAction: (DuringRunAction) -> Unit = {},
    queuedPausedCount: Int = 0,
    onSendQueuedMessages: () -> Unit = {},
    isEditingQueued: Boolean = false,
    onCommitEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    isAwaitingUploadSend: Boolean = false,
    arePicksUnsettled: Boolean = false,
    onCancelPendingSend: () -> Unit = {},
    queuedMessages: List<QueuedMessage> = emptyList(),
    onEditQueuedMessage: (localId: String) -> Unit = {},
    onCancelQueuedMessage: (localId: String) -> Unit = {},
    onReorderQueuedMessages: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    fontSizeMultiplier: Float = 1f,
    enabledTools: Set<String> = emptySet(),
    mcpServers: List<McpServerDisplayData> = emptyList(),
    selectedMcpServerNames: Set<String> = emptySet(),
    isRecording: Boolean = false,
    isTranscribing: Boolean = false,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    selectedModelDisplay: String? = null,
    isCodeInterpreterAvailable: Boolean = true,
    attachedFiles: List<AttachedFile> = emptyList(),
    onRemoveFile: (AttachedFile) -> Unit = {},
    hasClipboardImage: Boolean = false,
    onPasteImage: (() -> Unit)? = null,
    gates: ChatInputGates = ChatInputGates(),
    contextUsage: ContextUsage? = null,
    tokenUsage: TokenUsage? = null,
    contextUsageEnabled: Boolean = false,
    contextBarPlacement: ContextBarPlacement = ContextBarPlacement.OPTIONS_SHEET,
    promptSuggestions: List<PromptMentionDisplayData> = emptyList(),
    onSlashCommandSelected: (PromptMentionDisplayData) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val state = ChatInputState(
        inputText = inputText,
        isStreaming = isStreaming,
        isRecording = isRecording,
        isTranscribing = isTranscribing,
        enabledTools = enabledTools,
        mcpServers = mcpServers,
        selectedMcpServerNames = selectedMcpServerNames,
        selectedModelDisplay = selectedModelDisplay,
        isCodeInterpreterAvailable = isCodeInterpreterAvailable,
        attachedFiles = attachedFiles,
        gates = gates,
        canQueue = canQueue,
        canSteer = canSteer,
        duringRunAction = duringRunAction,
        duringRunSendTarget = duringRunSendTarget,
        pendingSteers = pendingSteers,
        isEditingQueued = isEditingQueued,
        isAwaitingUploadSend = isAwaitingUploadSend,
        arePicksUnsettled = arePicksUnsettled,
        contextUsage = contextUsage,
        tokenUsage = tokenUsage,
        contextUsageEnabled = contextUsageEnabled,
        contextBarPlacement = contextBarPlacement,
        promptSuggestions = promptSuggestions,
    )

    CommonChatInputCore(
        state = state,
        onSend = onSend,
        onStop = onStop,
        onSelectPrompt = onSlashCommandSelected,
        onQueue = onQueue,
        onDuringRunSend = onDuringRunSend,
        onSteer = onSteer,
        onCancelSteer = onCancelSteer,
        onSetDuringRunAction = onSetDuringRunAction,
        queuedPausedCount = queuedPausedCount,
        onSendQueuedMessages = onSendQueuedMessages,
        onCommitEdit = onCommitEdit,
        onCancelEdit = onCancelEdit,
        onCancelPendingSend = onCancelPendingSend,
        queuedMessages = queuedMessages,
        onEditQueuedMessage = onEditQueuedMessage,
        onCancelQueuedMessage = onCancelQueuedMessage,
        onReorderQueuedMessages = onReorderQueuedMessages,
        fontSizeMultiplier = fontSizeMultiplier,
        onRemoveFile = onRemoveFile,
        modifier = modifier,
        onOpenModelSelector = onOpenModelSelector,
        leadingButtons = {
            // "+" button to open tools bottom sheet, with the active-tool count (same as Android).
            ToolsButton(
                activeTools = activeToolCount(state),
                onClick = onOpenTools,
                contentDescription = stringResource(Res.string.cd_attach_file),
            )

            // Paste image button (shown when clipboard has image content)
            if (hasClipboardImage && onPasteImage != null) {
                IconButton(
                    onClick = onPasteImage,
                    modifier = Modifier.size(ChatInputDefaults.controlSize),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = stringResource(Res.string.cd_paste_image),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

        },
        textFieldContent = {
            // Nothing overlays the field any more: the mic lives in the controls row, so the old
            // iOS text-magnifier conflict with an overlaid button is gone with the overlay.
            TextField(
                value = inputText,
                onValueChange = onInputChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp, max = 160.dp),
                placeholder = {
                    ChatInputPlaceholder(
                        isRecording = isRecording,
                        selectedModelDisplay = selectedModelDisplay,
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = ChatInputDefaults.embeddedTextFieldColors(),
                keyboardOptions = ChatInputDefaults.keyboardOptions,
                keyboardActions = KeyboardActions.Default,
                maxLines = 6,
            )
        },
        micButton = {
            IconButton(
                onClick = {
                    if (isRecording) {
                        onStopRecording()
                    } else {
                        onStartRecording()
                    }
                },
                modifier = Modifier.size(ChatInputDefaults.controlSize),
                enabled = !isTranscribing,
            ) {
                VoiceMicIndicator(
                    isRecording = isRecording,
                    isTranscribing = isTranscribing,
                )
            }
        },
        bottomContent = {
            // Snackbar for file attachment toast
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        },
    )

}
