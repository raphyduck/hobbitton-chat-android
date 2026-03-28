package com.librechat.android.feature.chat.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.librechat.android.feature.chat.McpServerDisplayData
import com.librechat.android.feature.chat.PromptMentionDisplayData
import com.librechat.android.feature.chat.R
import java.io.File

data class AttachedFile(
    val uri: Uri,
    val name: String,
    val isImage: Boolean = false,
    val uploadProgress: Float? = null,
    /** Server-assigned file ID after successful upload. Null while uploading. */
    val fileId: String? = null,
    /** Server file path returned from upload. */
    val filepath: String? = null,
    /** MIME type of the file. */
    val type: String? = null,
    /** Image width in pixels (if applicable). */
    val width: Int? = null,
    /** Image height in pixels (if applicable). */
    val height: Int? = null,
    /** Whether the upload has failed. */
    val uploadFailed: Boolean = false,
)

@Composable
fun ChatInput(
    inputText: String,
    isStreaming: Boolean,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    attachedFiles: List<AttachedFile> = emptyList(),
    onFilesSelected: (List<Uri>) -> Unit = {},
    onRemoveFile: (AttachedFile) -> Unit = {},
    promptSuggestions: List<PromptMentionDisplayData> = emptyList(),
    onPromptSelected: (PromptMentionDisplayData) -> Unit = {},
    onSlashCommandSelected: (PromptMentionDisplayData) -> Unit = {},
    isRecording: Boolean = false,
    isTranscribing: Boolean = false,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onImagePasted: ((Uri) -> Unit)? = null,
    enabledTools: Set<String> = emptySet(),
    onToggleTool: (String) -> Unit = {},
    mcpServers: List<McpServerDisplayData> = emptyList(),
    selectedMcpServerNames: Set<String> = emptySet(),
    onToggleMcpServer: (String) -> Unit = {},
    onOpenModelParameters: () -> Unit = {},
    onOpenModelSelector: () -> Unit = {},
    selectedModelDisplay: String? = null,
    isCodeInterpreterAvailable: Boolean = true,
) {
    val cdOpenToolsMenu = stringResource(R.string.cd_open_tools_menu)
    val cdPasteImage = stringResource(R.string.cd_paste_image)
    val cdMessageInput = stringResource(R.string.cd_message_input)
    val cdStopVoiceRec = stringResource(R.string.cd_stop_voice_recording)
    val cdStartVoiceRec = stringResource(R.string.cd_start_voice_recording)
    val cdStopGen = stringResource(R.string.cd_stop_generation)
    val cdSendMsg = stringResource(R.string.cd_send_message)
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onFilesSelected(uris)
        }
    }

    // Photo picker (gallery) launcher using modern PickMultipleVisualMedia
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onFilesSelected(uris)
        }
    }

    // Camera launcher: stores the photo in a temp file via FileProvider
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraPhotoUri
        if (success && uri != null) {
            onFilesSelected(listOf(uri))
        }
        cameraPhotoUri = null
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            val photoFile = createCameraPhotoFile(context)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile,
            )
            cameraPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    val onTakePhoto: () -> Unit = {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission) {
            val photoFile = createCameraPhotoFile(context)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile,
            )
            cameraPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val onPickPhotos: () -> Unit = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    val focusRequester = remember { FocusRequester() }
    var showToolsSheet by remember { mutableStateOf(false) }

    // Use TextFieldValue internally so we can control cursor position.
    // When inputText changes externally (e.g. STT result, prompt insertion),
    // place the cursor at the end of the new text.
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(inputText, selection = TextRange(inputText.length)))
    }
    if (textFieldValue.text != inputText) {
        textFieldValue = TextFieldValue(inputText, selection = TextRange(inputText.length))
    }

    val hasActiveTools by remember(enabledTools, selectedMcpServerNames) {
        derivedStateOf {
            enabledTools.isNotEmpty() || selectedMcpServerNames.isNotEmpty()
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        surfaceColor.copy(alpha = 0.7f),
                        surfaceColor.copy(alpha = 0.95f),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Floating attachment and tool indicator chips
            AttachmentChipsRow(
                attachedFiles = attachedFiles,
                enabledTools = enabledTools,
                onRemoveFile = onRemoveFile,
                mcpServers = mcpServers,
                selectedMcpServerNames = selectedMcpServerNames,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // "+" button to open tools bottom sheet
                Box {
                    FilledTonalIconButton(
                        onClick = { showToolsSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = cdOpenToolsMenu
                                role = Role.Button
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                        )
                    }
                    // Active tools badge
                    if (hasActiveTools) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = (-2).dp, y = 2.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                ),
                        )
                    }
                }

                // Paste image button (shown only when clipboard has image content)
                if (onImagePasted != null) {
                    IconButton(
                        onClick = {
                            // Clipboard image pasting is handled by the screen
                            onImagePasted(Uri.EMPTY)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = cdPasteImage
                                role = Role.Button
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Detect @mention query from input text
                val mentionQuery by remember(inputText) {
                    derivedStateOf {
                        val atIndex = inputText.lastIndexOf('@')
                        if (atIndex >= 0) {
                            val afterAt = inputText.substring(atIndex + 1)
                            // Only show suggestions if there's no space after @ (still typing the mention)
                            if (!afterAt.contains(' ')) afterAt else null
                        } else {
                            null
                        }
                    }
                }

                val filteredPrompts by remember(mentionQuery, promptSuggestions) {
                    derivedStateOf {
                        val query = mentionQuery
                        if (query != null && promptSuggestions.isNotEmpty()) {
                            promptSuggestions.filter { group ->
                                group.name.contains(query, ignoreCase = true) ||
                                    group.command?.contains(query, ignoreCase = true) == true
                            }.take(5)
                        } else {
                            emptyList()
                        }
                    }
                }

                // Detect slash command query: "/" at position 0
                val slashQuery by remember(inputText) {
                    derivedStateOf {
                        if (inputText.startsWith("/")) {
                            val afterSlash = inputText.substring(1)
                            // Only show suggestions if there's no space (still typing the command)
                            if (!afterSlash.contains(' ')) afterSlash else null
                        } else {
                            null
                        }
                    }
                }

                val filteredSlashCommands by remember(slashQuery, promptSuggestions) {
                    derivedStateOf {
                        val query = slashQuery
                        if (query != null && promptSuggestions.isNotEmpty()) {
                            promptSuggestions.filter { group ->
                                val cmd = group.command
                                cmd != null && cmd.contains(query, ignoreCase = true)
                            }.take(5)
                        } else {
                            emptyList()
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            onInputChanged(newValue.text)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp, max = 160.dp)
                            .focusRequester(focusRequester)
                            .semantics {
                                contentDescription = cdMessageInput
                            },
                        placeholder = {
                            Text(
                                text = if (isRecording) {
                                    stringResource(R.string.recording)
                                } else if (!selectedModelDisplay.isNullOrBlank()) {
                                    stringResource(R.string.hint_message_model, selectedModelDisplay)
                                } else {
                                    stringResource(R.string.hint_message)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Default,
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        keyboardActions = KeyboardActions.Default,
                        maxLines = 6,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (isRecording) {
                                        onStopRecording()
                                    } else {
                                        onStartRecording()
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .semantics {
                                        contentDescription = if (isRecording) {
                                            cdStopVoiceRec
                                        } else {
                                            cdStartVoiceRec
                                        }
                                        role = Role.Button
                                    },
                                enabled = !isTranscribing,
                            ) {
                                if (isTranscribing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else if (isRecording) {
                                    val infiniteTransition = rememberInfiniteTransition(
                                        label = "recording_pulse",
                                    )
                                    val pulseAlpha by infiniteTransition.animateFloat(
                                        initialValue = 1f,
                                        targetValue = 0.3f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(600),
                                            repeatMode = RepeatMode.Reverse,
                                        ),
                                        label = "pulse_alpha",
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .alpha(pulseAlpha)
                                            .background(
                                                color = MaterialTheme.colorScheme.error,
                                                shape = CircleShape,
                                            ),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )

                    // @mention dropdown
                    DropdownMenu(
                        expanded = filteredPrompts.isNotEmpty() && filteredSlashCommands.isEmpty(),
                        onDismissRequest = { /* Dismissed by typing or selecting */ },
                    ) {
                        filteredPrompts.forEach { group ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = group.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        val oneliner = group.oneliner
                                        if (!oneliner.isNullOrBlank()) {
                                            Text(
                                                text = oneliner,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                },
                                onClick = { onPromptSelected(group) },
                            )
                        }
                    }

                    // Slash command dropdown
                    SlashCommandMenu(
                        filteredCommands = filteredSlashCommands,
                        onCommandSelected = onSlashCommandSelected,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Send / Stop button
                AnimatedContent(
                    targetState = isStreaming,
                    transitionSpec = {
                        (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                    },
                    label = "send_stop_toggle",
                ) { streaming ->
                    if (streaming) {
                        IconButton(
                            onClick = onStop,
                            modifier = Modifier
                                .size(56.dp)
                                .semantics {
                                    contentDescription = cdStopGen
                                    role = Role.Button
                                },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.error,
                                        shape = CircleShape,
                                    ),
                            )
                        }
                    } else {
                        val canSend = inputText.isNotBlank() || attachedFiles.isNotEmpty()
                        IconButton(
                            onClick = onSend,
                            modifier = Modifier
                                .size(56.dp)
                                .semantics {
                                    contentDescription = cdSendMsg
                                    role = Role.Button
                                },
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
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }

    // Tools bottom sheet
    if (showToolsSheet) {
        ChatToolsBottomSheet(
            enabledTools = enabledTools,
            onToggleTool = onToggleTool,
            mcpServers = mcpServers,
            selectedMcpServerNames = selectedMcpServerNames,
            onToggleMcpServer = onToggleMcpServer,
            onAttachFiles = { filePickerLauncher.launch(arrayOf("*/*")) },
            onTakePhoto = onTakePhoto,
            onPickPhotos = onPickPhotos,
            onOpenModelParameters = onOpenModelParameters,
            onOpenModelSelector = onOpenModelSelector,
            selectedModelDisplay = selectedModelDisplay,
            onDismiss = { showToolsSheet = false },
            isCodeInterpreterAvailable = isCodeInterpreterAvailable,
        )
    }
}

/**
 * Creates a temporary file for the camera to write a photo into.
 * Stored in the app's cache directory under `camera_photos/` which is
 * registered in the FileProvider paths XML.
 */
private fun createCameraPhotoFile(context: android.content.Context): File {
    val cameraDir = File(context.cacheDir, "camera_photos")
    if (!cameraDir.exists()) {
        cameraDir.mkdirs()
    }
    return File.createTempFile("photo_", ".jpg", cameraDir)
}
