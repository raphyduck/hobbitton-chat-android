package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.engine.SHELL_CONNECTOR
import com.garfiec.librechat.core.data.engine.TranscriptionOutcome
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.ui.input.ChatInputBox
import com.garfiec.librechat.core.ui.input.ChatInputDefaults
import com.garfiec.librechat.core.ui.input.ChatInputField
import com.garfiec.librechat.core.ui.input.ChatInputPill
import com.garfiec.librechat.core.ui.input.ComposerSendButton
import com.garfiec.librechat.feature.tasks.components.ConnectorPickerSheet
import com.garfiec.librechat.feature.tasks.components.MissionDictation
import com.garfiec.librechat.feature.tasks.components.MissionSpeaker
import com.garfiec.librechat.feature.tasks.components.ModelPickerSheet
import com.garfiec.librechat.feature.tasks.components.rememberMissionAttachmentPicker
import com.garfiec.librechat.feature.tasks.components.rememberMissionAudioPicker
import com.garfiec.librechat.feature.tasks.components.rememberMissionDictation
import com.garfiec.librechat.feature.tasks.components.rememberMissionFilePicker
import com.garfiec.librechat.feature.tasks.components.rememberMissionSpeaker
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.chat_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_attach_audio
import com.garfiec.librechat.feature.tasks.resources.tasks_attach_file
import com.garfiec.librechat.feature.tasks.resources.tasks_attach_photo
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_add
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_connector_count
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_connectors_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_model_blind
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_models_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_send
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_shell_on
import com.garfiec.librechat.feature.tasks.resources.tasks_dictate
import com.garfiec.librechat.feature.tasks.resources.tasks_dictate_stop
import com.garfiec.librechat.feature.tasks.resources.tasks_hands_free_listening
import com.garfiec.librechat.feature.tasks.resources.tasks_hands_free_needs_phone
import com.garfiec.librechat.feature.tasks.resources.tasks_listening
import com.garfiec.librechat.feature.tasks.resources.tasks_model_auto
import com.garfiec.librechat.feature.tasks.resources.tasks_model_default_short
import com.garfiec.librechat.feature.tasks.resources.tasks_recording
import com.garfiec.librechat.feature.tasks.resources.tasks_speaking
import com.garfiec.librechat.feature.tasks.resources.tasks_stop
import com.garfiec.librechat.feature.tasks.resources.tasks_transcribing
import com.garfiec.librechat.feature.tasks.util.StagedAttachment
import com.garfiec.librechat.feature.tasks.util.imageStagedForBlindModel
import com.garfiec.librechat.feature.tasks.util.message
import com.garfiec.librechat.feature.tasks.util.shortModelLabel
import com.garfiec.librechat.feature.tasks.util.title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The composer, laid out as Claude's: one raised box, the text on top, and under it a single row —
 * a thin-ringed « + » for what can be attached, the model and the connectors as plain labels with a
 * chevron, then the mic, and send only once there is something to send.
 *
 * It replaced a chips row floating above the box and a line of four icons beside it (photo, audio
 * file, mic, text, send), which left the text field a third of a phone's width; lot 2 (10/10/2026)
 * then took the fills off the pills and the frame off the field, so the row fits a 411 dp phone
 * without scrolling and the box reads as one object. Everything is still one tap from the box; the
 * two attachment kinds, used far less than the rest, live behind the « + ».
 *
 * Shape, fill and border come from `:core:ui`'s [ChatInputDefaults], so this box and the question
 * form cannot drift apart in the colours they share. The model and connectors write straight through
 * to the engine (`model` on the message, `PATCH /session/{id}` for the rules): controls, not
 * decoration.
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
    onDictate: (String) -> Unit,
    onDictationError: (TranscriptionOutcome.Failed) -> Unit,
    onAttachAudio: (bytes: ByteArray, mime: String, filename: String) -> Unit,
    onRemoveAudioNote: (String) -> Unit,
    onDismissTranscriptionError: () -> Unit,
    onSendSentence: (String) -> Unit,
    onSpeechEnd: () -> Unit,
    onHandsFreeOff: () -> Unit,
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
    // Nothing is sent on its own. Heard by the phone itself unless Settings says otherwise.
    // Hands-free (28/10/2026) is the one exception: a sentence heard goes out on its own, and the
    // reply comes back through the phone's voice before the mic listens again.
    val handsFree by rememberUpdatedState(state.handsFree)
    val dictation = rememberMissionDictation(
        onDevice = state.localDictation,
        onCapture = { onTranscribeAudio(it.bytes, it.mime, it.filename) },
        onHear = { if (handsFree) onSendSentence(it) else onDictate(it) },
        onError = onDictationError,
    )
    val speaker = rememberMissionSpeaker(onDone = onSpeechEnd)
    var handsFreeRefused by remember { mutableStateOf(false) }
    HandsFreeLoop(
        state = state,
        dictation = dictation,
        speaker = speaker,
        onSpeechEnd = onSpeechEnd,
        onRefuse = {
            handsFreeRefused = true
            onHandsFreeOff()
        },
    )

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
        if (handsFreeRefused) {
            ComposerNotice(stringResource(Res.string.tasks_hands_free_needs_phone), onDismiss = { handsFreeRefused = false })
        }
        when {
            speaker?.speaking == true -> ComposerStatus(stringResource(Res.string.tasks_speaking))
            // The phone's recognition shows the words as they come; a recording has none to show yet.
            dictation?.listening == true -> ComposerStatus(
                dictation.heard.ifBlank {
                    stringResource(if (state.handsFree) Res.string.tasks_hands_free_listening else Res.string.tasks_listening)
                },
            )
            dictation?.recording == true -> ComposerStatus(stringResource(Res.string.tasks_recording))
            state.transcribing -> ComposerStatus(stringResource(Res.string.tasks_transcribing))
        }
        state.sendError?.let { kind ->
            // The send failed and the text was put back — say why, once, dismissible on tap.
            ComposerNotice(stringResource(kind.title()), onDismissError)
        }
        if (imageStagedForBlindModel(state.effectiveModel, state.prices, state.attachments)) {
            // The photo will reach this model as words (the gateway describes it): say so before
            // it leaves, and let a tap pick a model that sees, for this conversation.
            ComposerNotice(
                text = stringResource(
                    Res.string.tasks_chat_model_blind,
                    shortModelLabel(state.effectiveModel?.label.orEmpty()),
                ),
                onDismiss = { picker = Picker.MODELS },
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        ChatInputBox(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)) {
            if (state.attachments.isNotEmpty() || state.audioNotes.isNotEmpty()) {
                StagedAttachmentsRow(state.attachments, state.audioNotes, onRemoveAttachment, onRemoveAudioNote)
            }
            ChatInputField(
                value = state.input,
                onValueChange = onInput,
                placeholder = stringResource(Res.string.chat_hint),
                maxLines = MAX_INPUT_LINES,
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AddButton(
                    openPhoto = openPhoto,
                    openFile = openFile,
                    openAudio = openAudio,
                    audioEnabled = !state.transcribing,
                )
                // The labels scroll among themselves, so a long model name never pushes send off
                // the row. The edge fades while more is hidden, so a cut label reads as « there is
                // more » rather than as a clipped one.
                val pillsScroll = rememberScrollState()
                Row(
                    Modifier
                        .weight(1f)
                        .fadingEnd(visible = pillsScroll.canScrollForward)
                        .horizontalScroll(pillsScroll),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
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

    when (picker) {
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
        // Direct connectors only (24/10/2026, Raphaël): what the annuaire serves is the
        // scheduler's choice, from who speaks to the session, and is not offered here.
        Picker.CONNECTORS -> ConnectorPickerSheet(
            options = state.connectors.filter { !it.viaAnnuaire },
            ticked = state.enabledConnectors.orEmpty(),
            onToggle = onToggleConnector,
            onDismiss = { picker = Picker.NONE },
        )
        Picker.NONE -> Unit
    }
}

private enum class Picker { NONE, MODELS, CONNECTORS }

/**
 * One line above the box that says what went wrong, dismissed by a tap on it; in another [color],
 * a warning whose tap acts rather than dismisses.
 */
@Composable
private fun ComposerNotice(text: String, onDismiss: () -> Unit, color: Color = MaterialTheme.colorScheme.error) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss)
            .padding(horizontal = 20.dp, vertical = 6.dp),
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
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
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

/**
 * Hands-free's turn-taking (28/10/2026): the mic listens whenever nobody is talking, and stops
 * while Eva answers or reads; a reply that lands is read aloud, then the mic listens again. Off,
 * a reading is cut and the mic hands-free had opened is closed; a dictation the person started
 * themselves is left alone. The recording for the scheduler cannot take turns (it ends at a tap,
 * not at a pause): hands-free is refused when that is all the phone has.
 */
@Composable
private fun HandsFreeLoop(
    state: MissionChatUiState,
    dictation: MissionDictation?,
    speaker: MissionSpeaker?,
    onSpeechEnd: () -> Unit,
    onRefuse: () -> Unit,
) {
    val handsFree = state.handsFree
    val speechEnd by rememberUpdatedState(onSpeechEnd)
    val refuse by rememberUpdatedState(onRefuse)
    LaunchedEffect(state.toSpeak, handsFree) {
        val text = state.toSpeak ?: return@LaunchedEffect
        if (handsFree && speaker != null) speaker.speak(text) else speechEnd()
    }
    var micOpenedHere by remember { mutableStateOf(false) }
    val speaking = speaker?.speaking == true
    val talking = state.chat.streaming || state.sending || speaking || state.toSpeak != null
    val recording = dictation?.recording == true
    val listening = dictation?.listening == true
    LaunchedEffect(handsFree, talking, recording, listening) {
        if (dictation == null || speaker == null) {
            if (handsFree) refuse()
            return@LaunchedEffect
        }
        when {
            !handsFree -> {
                if (speaking) speaker.stop()
                if (micOpenedHere && recording) dictation.toggle()
                micOpenedHere = false
            }
            recording && !listening -> {
                dictation.toggle()
                refuse()
            }
            talking && recording && micOpenedHere -> dictation.toggle()
            !talking && !recording -> {
                micOpenedHere = true
                dictation.toggle()
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
            .padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

/**
 * The « + »: what can be attached to the next message, a thin ring rather than a filled disc so it
 * sits in the row without weighing on it. Absent when the platform offers neither picker — a
 * button that opens an empty menu is worse than no button.
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
        IconButton(
            onClick = { open = true },
            modifier = Modifier
                .size(ChatInputDefaults.controlSize)
                .border(1.dp, ChatInputDefaults.borderColor, CircleShape),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(Res.string.tasks_chat_add),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
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
 * The labels under the text: the model the next message runs on, then whether the shell is open.
 *
 * No connector choice since 22/10/2026 (D-083, Raphaël's rule): the scheduler decides what a session
 * reaches from WHO speaks to it, the titulaire here, not from ticks the caller sends. The one thing
 * that level leaves on demand is the shell, which inherits everything: one tap opens or closes it.
 * The model leads, as on Claude's composer.
 *
 * The two are independent: the connectors come from the scheduler and the models from the engine,
 * so one host being unreachable leaves the other's label standing. A single row that vanished
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
        // Capped: the model's name is the longest thing on the row, and the connectors' label
        // beside it should stay in view. The provider is in the picker, not on the label.
        state.models.isNotEmpty() -> ComposerPill(
            label = state.effectiveModel?.let { if (it.isAuto) stringResource(Res.string.tasks_model_auto) else shortModelLabel(it.label) }
                ?: stringResource(Res.string.tasks_model_default_short),
            onClick = onOpenModels,
            modifier = Modifier.widthIn(max = MODEL_PILL_MAX_WIDTH),
        )
    }
    when {
        state.connectorsError != null -> ComposerPill(
            label = stringResource(Res.string.tasks_chat_connectors_failed),
            icon = Icons.Outlined.Refresh,
            onClick = onRetryCatalogue,
        )
        // Not read back yet: no label rather than a claim the screen has no grounds for. The count
        // is of the DIRECT connectors ticked, the only ones the sheet offers (24/10/2026); the
        // shell, when ticked, is named, being the one the scheduler leaves on demand.
        state.connectors.isNotEmpty() && state.enabledConnectors != null -> {
            val direct = state.connectors.filter { !it.viaAnnuaire }.map { it.name }.toSet()
            val ticked = state.enabledConnectors.count { it in direct }
            val label = pluralStringResource(Res.plurals.tasks_chat_connector_count, ticked, ticked)
            ComposerPill(
                label = if (SHELL_CONNECTOR in state.enabledConnectors) {
                    label + " · " + stringResource(Res.string.tasks_chat_shell_on)
                } else {
                    label
                },
                onClick = onOpenConnectors,
            )
        }
    }
}

/** [ChatInputPill] with this screen's optional leading icon (only the retry arrow, today). */
@Composable
private fun ComposerPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    ChatInputPill(
        label = label,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = icon?.let {
            { Icon(it, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    )
}

/**
 * Fades the end of a scrolling row out while [visible] — more of it is scrolled away — by masking
 * what was drawn rather than painting a colour over it, so it holds on any background.
 */
private fun Modifier.fadingEnd(visible: Boolean): Modifier = if (!visible) {
    this
} else {
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = PILLS_FADE_WIDTH.toPx()
            val rtl = layoutDirection == LayoutDirection.Rtl
            val kept = listOf(Color.Black, Color.Transparent)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = if (rtl) kept.reversed() else kept,
                    startX = if (rtl) 0f else size.width - fade,
                    endX = if (rtl) fade else size.width,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}

private const val MAX_INPUT_LINES = 6
private val MODEL_PILL_MAX_WIDTH = 150.dp
private val PILLS_FADE_WIDTH = 24.dp
