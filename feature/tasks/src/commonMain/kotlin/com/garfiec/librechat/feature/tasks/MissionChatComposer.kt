package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.ui.input.ChatInputBox
import com.garfiec.librechat.core.ui.input.ChatInputDefaults
import com.garfiec.librechat.core.ui.input.ChatInputPill
import com.garfiec.librechat.core.ui.input.ComposerSendButton
import com.garfiec.librechat.feature.tasks.components.ConnectorPickerSheet
import com.garfiec.librechat.feature.tasks.components.MissionDictation
import com.garfiec.librechat.feature.tasks.components.ModelPickerSheet
import com.garfiec.librechat.feature.tasks.components.rememberMissionAttachmentPicker
import com.garfiec.librechat.feature.tasks.components.rememberMissionAudioPicker
import com.garfiec.librechat.feature.tasks.components.rememberMissionFilePicker
import com.garfiec.librechat.feature.tasks.components.rememberMissionDictation
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_attach_audio
import com.garfiec.librechat.feature.tasks.resources.tasks_attach_file
import com.garfiec.librechat.feature.tasks.resources.tasks_attach_photo
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_add
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_connector_count
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_connectors_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_models_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_no_connector
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_send
import com.garfiec.librechat.feature.tasks.resources.tasks_connectors
import com.garfiec.librechat.feature.tasks.resources.tasks_dictate
import com.garfiec.librechat.feature.tasks.resources.tasks_dictate_stop
import com.garfiec.librechat.feature.tasks.resources.tasks_model_default_short
import com.garfiec.librechat.feature.tasks.resources.tasks_recording
import com.garfiec.librechat.feature.tasks.resources.tasks_stop
import com.garfiec.librechat.feature.tasks.resources.tasks_transcribing
import com.garfiec.librechat.feature.tasks.util.StagedAttachment
import com.garfiec.librechat.feature.tasks.util.message
import com.garfiec.librechat.feature.tasks.util.title
import org.jetbrains.compose.resources.stringResource

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
internal fun MissionChatInput(
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
    onTranscribeAudio: (bytes: ByteArray, mime: String, filename: String) -> Unit,
    onAttachAudio: (bytes: ByteArray, mime: String, filename: String) -> Unit,
    onRemoveAudioNote: (String) -> Unit,
    onDismissTranscriptionError: () -> Unit,
) {
    var picker by remember { mutableStateOf(Picker.NONE) }
    // Null where the platform has nothing to offer (iOS today) — then no menu entry, rather than an
    // entry that does nothing. Called here, unconditionally: they remember launchers.
    val openPhoto = rememberMissionAttachmentPicker(onPick = onAddAttachments)
    val openFile = rememberMissionFilePicker(onPick = onAddAttachments)
    // A deposited audio file goes to the THREAD: transcribed on pick, staged as a quoted note, sent
    // with the message. Transcribed by the scheduler because no model on the gateway hears audio.
    val openAudio = rememberMissionAudioPicker(onPick = { onAttachAudio(it.bytes, it.mime, it.filename) })
    // The mic DICTATES: tap to record, tap to stop, and the words land in the box — where the
    // speaker reads what was heard before it becomes an instruction (asked for on 31/08/2026).
    // Nothing is sent on its own.
    val dictation = rememberMissionDictation(onCapture = { onTranscribeAudio(it.bytes, it.mime, it.filename) })

    Surface {
        // The keyboard, then the navigation bar — whichever is taller, never both stacked.
        //
        // `navigationBarsPadding()` alone left the composer *behind* the keyboard: it is the
        // Scaffold's bottomBar, so nothing lifts it on its own (reported 30/08/2026). Adding
        // `imePadding()` on top would stack the two; `union` takes the larger.
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
            state.transcriptionError?.let { failed ->
                // The translated cause, then the server's own words when it gave some.
                val text = listOfNotNull(stringResource(failed.failure.message()), failed.reason).joinToString(" — ")
                ComposerNotice(text, onDismissTranscriptionError)
            }
            when {
                dictation?.recording == true -> ComposerStatus(stringResource(Res.string.tasks_recording))
                state.transcribing -> ComposerStatus(stringResource(Res.string.tasks_transcribing))
            }
            state.sendError?.let { kind ->
                // The send failed and the text was put back — say why, once, dismissible on tap.
                ComposerNotice(stringResource(kind.title()), onDismissError)
            }

            ChatInputBox(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (state.attachments.isNotEmpty() || state.audioNotes.isNotEmpty()) {
                    StagedAttachmentsRow(state.attachments, state.audioNotes, onRemoveAttachment, onRemoveAudioNote)
                }
                TextField(
                    value = state.input,
                    onValueChange = onInput,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(Res.string.tasks_chat_hint)) },
                    colors = ChatInputDefaults.embeddedTextFieldColors(),
                    keyboardOptions = ChatInputDefaults.keyboardOptions,
                    maxLines = MAX_INPUT_LINES,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AddButton(
                        openPhoto = openPhoto,
                        openFile = openFile,
                        openAudio = openAudio,
                        audioEnabled = !state.transcribing,
                    )
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
                    if (dictation != null) DictationButton(dictation, transcribing = state.transcribing)
                    ComposerSendButton(
                        // `sending` counts as running: the gap between the POST and the answer's
                        // first token is exactly when someone wants to be able to call it off.
                        running = state.chat.streaming || state.sending,
                        // A photo — or a transcribed audio — can be the whole message.
                        canSend = state.input.isNotBlank() ||
                            state.attachments.isNotEmpty() ||
                            state.audioNotes.isNotEmpty(),
                        onSend = onSend,
                        onStop = onStop,
                        sendContentDescription = stringResource(Res.string.tasks_chat_send),
                        stopContentDescription = stringResource(Res.string.tasks_stop),
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
 * The mic: tap to record, tap again to stop and transcribe. While the words are on their way it
 * turns into a spinner, so a second recording cannot start behind the first.
 */
@Composable
private fun DictationButton(dictation: MissionDictation, transcribing: Boolean) {
    Box(Modifier.size(ChatInputDefaults.controlSize), contentAlignment = Alignment.Center) {
        if (transcribing) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = dictation.toggle, modifier = Modifier.size(ChatInputDefaults.controlSize)) {
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
}

/** One quiet line above the box that says what the mic is doing: recording, or transcribing. */
@Composable
private fun ComposerStatus(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * The « + »: what can be attached to the next message. Absent when the platform offers neither
 * picker — a button that opens an empty menu is worse than no button.
 */
@Composable
private fun AddButton(
    openPhoto: (() -> Unit)?,
    openFile: (() -> Unit)?,
    openAudio: (() -> Unit)?,
    audioEnabled: Boolean,
) {
    if (openPhoto == null && openFile == null && openAudio == null) return
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconButton(onClick = { open = true }, modifier = Modifier.size(ChatInputDefaults.controlSize)) {
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
            openFile?.let { pick ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.tasks_attach_file)) },
                    leadingIcon = { Icon(Icons.Outlined.AttachFile, null) },
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

/** [ChatInputPill] with this screen's optional leading icon. */
@Composable
private fun ComposerPill(label: String, onClick: () -> Unit, icon: ImageVector? = null) {
    ChatInputPill(
        label = label,
        onClick = onClick,
        leadingIcon = icon?.let {
            { Icon(it, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    )
}

private const val MAX_INPUT_LINES = 6
