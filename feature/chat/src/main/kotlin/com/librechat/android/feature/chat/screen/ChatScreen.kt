package com.librechat.android.feature.chat.screen

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.librechat.android.core.model.ContentType
import com.librechat.android.core.model.MessageContentPart
import com.librechat.android.feature.chat.util.MessageNode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.librechat.android.core.common.EndpointConstants
import com.librechat.android.core.ui.components.LoadingIndicator
import com.librechat.android.feature.chat.components.ChatInput
import com.librechat.android.feature.chat.components.ComparisonDualPane
import com.librechat.android.feature.chat.components.ComparisonTabBar
import com.librechat.android.feature.chat.components.ForkOptionsBottomSheet
import com.librechat.android.feature.chat.components.TempChatToggle
import com.librechat.android.feature.chat.components.InConvoSearchBar
import com.librechat.android.feature.chat.components.LandingContent
import com.librechat.android.feature.chat.components.MessageList
import com.librechat.android.feature.chat.components.ModelSelectorButton
import com.librechat.android.feature.chat.components.ModelSelectorSheet
import com.librechat.android.feature.chat.components.PresetPicker
import com.librechat.android.feature.chat.components.SavePresetDialog
import com.librechat.android.feature.chat.components.SecondaryMessageList
import com.librechat.android.core.ui.components.ModelParameterSheet
import com.librechat.android.core.data.datastore.ChatFontSize
import com.librechat.android.core.data.datastore.LatexRenderer
import com.librechat.android.core.ui.components.endpointIconRes
import com.librechat.android.core.ui.components.isMonochromeEndpointIcon
import com.librechat.android.feature.chat.viewmodel.ChatScreenState
import com.librechat.android.feature.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.librechat.android.feature.chat.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = koinViewModel(),
    onConversationStarted: ((String) -> Unit)? = null,
    onNavigateToConversation: ((String) -> Unit)? = null,
    onOpenDrawer: (() -> Unit)? = null,
    onNavigateToPromptsLibrary: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val attachedFiles by viewModel.attachedFiles.collectAsStateWithLifecycle()
    val shareLinkUrl by viewModel.shareLinkUrl.collectAsStateWithLifecycle()
    val prefs by viewModel.chatPreferences.collectAsStateWithLifecycle()
    val showImageDescriptions = prefs.showImageDescriptions
    val dismissKeyboardOnSend = prefs.dismissKeyboardOnSend
    val chatLayoutStyle = prefs.chatLayoutStyle
    val showAvatars = prefs.showAvatars
    val showBubbles = prefs.showBubbles
    val useKatex = prefs.latexRenderer == LatexRenderer.KATEX
    val sttEngine = prefs.sttEngine
    val sttLanguage = prefs.sttLanguage
    val keyboardController = LocalSoftwareKeyboardController.current
    val fontSizeMultiplier = when (uiState.chatFontSize) {
        ChatFontSize.SMALL -> 0.85f
        ChatFontSize.MEDIUM -> 1.0f
        ChatFontSize.LARGE -> 1.2f
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var showPresetPicker by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showSecondaryModelSheet by remember { mutableStateOf(false) }
    var activeComparisonTab by remember { mutableStateOf(0) }

    // Resolve agent name for model display
    val displayModel = if (uiState.selectedEndpoint == EndpointConstants.AGENTS && uiState.selectedModel != null) {
        uiState.agents.find { it.id == uiState.selectedModel }?.name ?: uiState.selectedModel
    } else {
        uiState.selectedModel
    }

    // Device speech recognizer launcher (used when server STT is not available)
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val transcribed = matches?.firstOrNull()
            if (!transcribed.isNullOrBlank()) {
                viewModel.onDeviceSpeechResult(transcribed)
            }
        }
    }

    // Runtime permission request for RECORD_AUDIO (server STT path)
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone permission is required for voice input")
            }
        }
    }

    val onStartRecordingWithPermission: () -> Unit = {
        val useServerStt = sttEngine.equals("whisper", ignoreCase = true) ||
            (sttEngine.isBlank() || sttEngine.equals("default", ignoreCase = true)) &&
            uiState.serverSttEnabled

        if (useServerStt) {
            if (!uiState.serverSttEnabled && sttEngine.equals("whisper", ignoreCase = true)) {
                // User selected Whisper but server STT is not configured
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        "Server speech-to-text is not enabled. " +
                            "Ask your server admin to enable STT, or switch to Device or Google engine.",
                    )
                }
            } else {
                // Server STT path: record audio and upload for transcription
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    viewModel.startRecording()
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        } else {
            // Device speech recognizer path (Device, Google, or Default without server)
            val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                // Apply user's STT language preference
                val languageLocale = mapSttLanguageToLocale(sttLanguage)
                if (languageLocale != null) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageLocale)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageLocale)
                }
            }
            // Apply user's STT engine preference (set the recognizer package)
            val enginePackage = mapSttEngineToPackage(sttEngine)
            if (enginePackage != null) {
                intent.setPackage(enginePackage)
            }
            try {
                speechRecognizerLauncher.launch(intent)
            } catch (_: Exception) {
                val engineLabel = if (sttEngine.equals("google", ignoreCase = true)) {
                    "Google speech recognition is not available. Is the Google app installed?"
                } else {
                    "Speech recognition is not available on this device"
                }
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(engineLabel)
                }
            }
        }
    }

    // When a new conversation starts, navigate to chat/{conversationId} immediately
    // (at StreamEvent.Created) so the new_chat landing page stays clean in the back
    // stack. The new ChatViewModel at chat/{id} will resume the active stream.
    // onPendingNavigationHandled() resets this ViewModel to a fresh landing state.
    LaunchedEffect(uiState.pendingNavigationConversationId) {
        val pendingId = uiState.pendingNavigationConversationId
        if (pendingId != null && onConversationStarted != null) {
            onConversationStarted(pendingId)
            viewModel.onPendingNavigationHandled()
        }
    }

    // Show errors in snackbar
    LaunchedEffect(uiState.error) {
        val error = uiState.error
        if (error != null) {
            snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "Dismiss",
            )
            viewModel.dismissError()
        }
    }

    // Stream resume on foreground
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    // Navigate to forked conversation
    LaunchedEffect(uiState.forkedConversationId) {
        val forkId = uiState.forkedConversationId
        if (forkId != null) {
            viewModel.onForkedConversationHandled()
            if (onNavigateToConversation != null) {
                onNavigateToConversation(forkId)
            } else if (onConversationStarted != null) {
                onConversationStarted(forkId)
            }
        }
    }

    // Navigate to duplicated conversation
    LaunchedEffect(uiState.duplicatedConversationId) {
        val dupId = uiState.duplicatedConversationId
        if (dupId != null) {
            viewModel.onDuplicatedConversationHandled()
            if (onNavigateToConversation != null) {
                onNavigateToConversation(dupId)
            } else if (onConversationStarted != null) {
                onConversationStarted(dupId)
            }
        }
    }

    // Copy share link to clipboard
    LaunchedEffect(shareLinkUrl) {
        val url = shareLinkUrl
        if (url != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("Share Link", url))
            viewModel.onShareLinkHandled()
            snackbarHostState.showSnackbar("Share link copied to clipboard")
        }
    }

    // Navigate back after delete/archive (conversationId becomes null)
    var hadConversation by remember { mutableStateOf(uiState.conversationId != null) }
    LaunchedEffect(uiState.conversationId) {
        if (hadConversation && uiState.conversationId == null) {
            onNavigateBack?.invoke()
        }
        hadConversation = uiState.conversationId != null
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            Column {
                ChatTopBar(
                    onLoadPreset = { showPresetPicker = true },
                    onSavePreset = { showSavePresetDialog = true },
                    onOpenDrawer = onOpenDrawer,
                    onOpenSearch = viewModel::openSearch,
                    onOpenPromptsLibrary = onNavigateToPromptsLibrary,
                    isTemporaryChat = uiState.isTemporaryChat,
                    onToggleTemporaryChat = viewModel::toggleTemporaryChat,
                    showTempChatToggle = uiState.conversationId == null,
                    isComparisonEnabled = uiState.comparisonState.isEnabled,
                    onToggleComparison = viewModel::toggleComparison,
                    conversationId = uiState.conversationId,
                    conversationTitle = uiState.conversationTitle,
                    sharedLinksEnabled = uiState.sharedLinksEnabled,
                    onShare = viewModel::shareConversation,
                    onRename = viewModel::showRenameDialog,
                    onDuplicate = viewModel::duplicateConversation,
                    onArchive = viewModel::archiveConversation,
                    onDelete = viewModel::showDeleteConfirmation,
                )
                // In-conversation search bar overlay
                if (uiState.isSearchOpen) {
                    InConvoSearchBar(
                        query = uiState.searchQuery,
                        onQueryChanged = viewModel::onSearchQueryChanged,
                        currentMatchIndex = uiState.currentSearchMatchIndex,
                        totalMatches = uiState.searchMatchIndices.size,
                        onPreviousMatch = viewModel::previousSearchMatch,
                        onNextMatch = viewModel::nextSearchMatch,
                        onClose = viewModel::closeSearch,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                when (uiState.screenState) {
                    ChatScreenState.LANDING -> {
                        LandingContent(
                            selectedModel = uiState.selectedModel,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ChatScreenState.LOADING -> {
                        LoadingIndicator(
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ChatScreenState.ACTIVE -> {
                        val comparisonState = uiState.comparisonState
                        if (comparisonState.isEnabled) {
                            val screenWidthDp = LocalConfiguration.current.screenWidthDp
                            val isWideScreen = screenWidthDp >= 600

                            val canBranch = comparisonState.parallelMessageId != null &&
                                !comparisonState.primaryIsStreaming &&
                                !comparisonState.secondaryIsStreaming

                            // Replace the parallel response message's content with
                            // the captured streaming buffer for each agent's pane.
                            // The server-loaded message may only contain the primary
                            // agent's content, so we substitute from the buffers.
                            val primarySenderName = run {
                                val model = uiState.selectedModel
                                if (uiState.selectedEndpoint == EndpointConstants.AGENTS && model != null) {
                                    uiState.agents.find { it.id == model }?.name ?: model
                                } else {
                                    model ?: "Assistant"
                                }
                            }
                            val primaryDisplayMessages = remember(
                                uiState.displayMessages,
                                comparisonState.parallelMessageId,
                                comparisonState.primaryFinalContent,
                            ) {
                                buildComparisonDisplayMessages(
                                    uiState.displayMessages,
                                    comparisonState.parallelMessageId,
                                    comparisonState.primaryFinalContent,
                                    primarySenderName,
                                )
                            }
                            val secondarySenderName = viewModel.getSecondaryModelDisplayName()
                                ?: comparisonState.secondaryModel ?: "Assistant"
                            val secondaryDisplayMessages = remember(
                                uiState.displayMessages,
                                comparisonState.parallelMessageId,
                                comparisonState.secondaryFinalContent,
                                secondarySenderName,
                            ) {
                                buildComparisonDisplayMessages(
                                    uiState.displayMessages,
                                    comparisonState.parallelMessageId,
                                    comparisonState.secondaryFinalContent,
                                    secondarySenderName,
                                )
                            }

                            val primaryMessageList: @Composable () -> Unit = {
                                MessageList(
                                    displayMessages = primaryDisplayMessages,
                                    isStreaming = comparisonState.primaryIsStreaming || uiState.isStreaming,
                                    streamingContent = if (comparisonState.primaryIsStreaming) comparisonState.primaryStreamingContent else uiState.streamingContent,
                                    activeToolCalls = if (comparisonState.primaryIsStreaming) comparisonState.primaryActiveToolCalls else uiState.activeToolCalls,
                                    onSiblingNavigation = viewModel::switchBranch,
                                    onEditMessage = viewModel::startEditing,
                                    onRegenerateMessage = viewModel::regenerateMessage,
                                    onCopyMessage = { messageId ->
                                        val text = viewModel.getMessageText(messageId)
                                        if (text.isNotBlank()) {
                                            clipboardManager.setPrimaryClip(
                                                ClipData.newPlainText("Message", text),
                                            )
                                        }
                                    },
                                    onFeedback = viewModel::submitFeedback,
                                    onContinue = { viewModel.continueGeneration() },
                                    onReadAloud = viewModel::readAloud,
                                    onFork = viewModel::showForkOptions,
                                    currentlyReadingMessageId = uiState.currentlyReadingMessageId,
                                    editingMessageId = uiState.editingMessageId,
                                    editingText = uiState.editingText,
                                    onEditTextChanged = viewModel::onEditTextChanged,
                                    onEditSaveAndSubmit = viewModel::submitEdit,
                                    onEditSaveOnly = viewModel::saveEditOnly,
                                    onEditCancel = viewModel::cancelEditing,
                                    baseUrl = uiState.serverUrl,
                                    fontSizeMultiplier = fontSizeMultiplier,
                                    isRefreshing = uiState.isRefreshingMessages,
                                    onRefresh = viewModel::refreshMessages,
                                    userAvatarUrl = uiState.userAvatarUrl,
                                    userName = uiState.userName,
                                    endpointIconRes = endpointIconRes(uiState.selectedEndpoint),
                                    tintEndpointIcon = isMonochromeEndpointIcon(uiState.selectedEndpoint),
                                    streamingSenderName = primarySenderName,
                                    showImageDescriptions = showImageDescriptions,
                                    chatLayoutStyle = chatLayoutStyle,
                                    showAvatars = showAvatars,
                                    showBubbles = showBubbles,
                                    useKatex = useKatex,
                                    searchQuery = if (uiState.isSearchOpen) uiState.searchQuery else null,
                                    searchMatchIndices = uiState.searchMatchIndices,
                                    currentSearchMatchIndex = uiState.currentSearchMatchIndex,
                                    searchScrollToIndex = uiState.searchScrollToIndex,
                                    onSearchScrollHandled = viewModel::onSearchScrollHandled,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            val secondaryEndpoint = comparisonState.secondaryEndpoint ?: "agents"
                            val secondaryModelName = viewModel.getSecondaryModelDisplayName()
                                ?: comparisonState.secondaryModel
                                ?: stringResource(R.string.select_model)

                            val secondaryMessageList: @Composable () -> Unit = {
                                SecondaryMessageList(
                                    displayMessages = secondaryDisplayMessages,
                                    isStreaming = comparisonState.secondaryIsStreaming,
                                    streamingContent = comparisonState.secondaryStreamingContent,
                                    activeToolCalls = comparisonState.secondaryActiveToolCalls,
                                    error = null,
                                    baseUrl = uiState.serverUrl,
                                    fontSizeMultiplier = fontSizeMultiplier,
                                    endpointIconRes = endpointIconRes(secondaryEndpoint),
                                    tintEndpointIcon = isMonochromeEndpointIcon(secondaryEndpoint),
                                    streamingSenderName = secondaryModelName,
                                    showImageDescriptions = showImageDescriptions,
                                    chatLayoutStyle = chatLayoutStyle,
                                    showAvatars = showAvatars,
                                    showBubbles = showBubbles,
                                    useKatex = useKatex,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            val onContinuePrimary = if (canBranch && comparisonState.primaryAgentId != null) {
                                { viewModel.branchFromComparison(comparisonState.primaryAgentId) }
                            } else {
                                null
                            }
                            val onContinueSecondary = if (canBranch && comparisonState.secondaryAgentId != null) {
                                { viewModel.branchFromComparison(comparisonState.secondaryAgentId) }
                            } else {
                                null
                            }

                            if (isWideScreen) {
                                // Tablet: dual pane side-by-side
                                ComparisonDualPane(
                                    primaryModelSelector = {
                                        ModelSelectorButton(
                                            modelName = displayModel,
                                            onClick = { showModelSheet = true },
                                        )
                                    },
                                    secondaryModelSelector = {
                                        ModelSelectorButton(
                                            modelName = secondaryModelName,
                                            onClick = { showSecondaryModelSheet = true },
                                        )
                                    },
                                    primaryContent = primaryMessageList,
                                    secondaryContent = secondaryMessageList,
                                    onContinueWithPrimary = onContinuePrimary,
                                    onContinueWithSecondary = onContinueSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                // Phone: tab bar with pager
                                ComparisonTabBar(
                                    primaryModelName = displayModel ?: "Primary",
                                    secondaryModelName = secondaryModelName,
                                    primaryContent = primaryMessageList,
                                    secondaryContent = secondaryMessageList,
                                    onContinueWithPrimary = onContinuePrimary,
                                    onContinueWithSecondary = onContinueSecondary,
                                    onTabChanged = { activeComparisonTab = it },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            MessageList(
                                displayMessages = uiState.displayMessages,
                                isStreaming = uiState.isStreaming,
                                streamingContent = uiState.streamingContent,
                                activeToolCalls = uiState.activeToolCalls,
                                onSiblingNavigation = viewModel::switchBranch,
                                onEditMessage = viewModel::startEditing,
                                onRegenerateMessage = viewModel::regenerateMessage,
                                onCopyMessage = { messageId ->
                                    val text = viewModel.getMessageText(messageId)
                                    if (text.isNotBlank()) {
                                        clipboardManager.setPrimaryClip(
                                            ClipData.newPlainText("Message", text),
                                        )
                                    }
                                },
                                onFeedback = viewModel::submitFeedback,
                                onContinue = { viewModel.continueGeneration() },
                                onReadAloud = viewModel::readAloud,
                                onFork = viewModel::showForkOptions,
                                currentlyReadingMessageId = uiState.currentlyReadingMessageId,
                                editingMessageId = uiState.editingMessageId,
                                editingText = uiState.editingText,
                                onEditTextChanged = viewModel::onEditTextChanged,
                                onEditSaveAndSubmit = viewModel::submitEdit,
                                onEditSaveOnly = viewModel::saveEditOnly,
                                onEditCancel = viewModel::cancelEditing,
                                baseUrl = uiState.serverUrl,
                                fontSizeMultiplier = fontSizeMultiplier,
                                isRefreshing = uiState.isRefreshingMessages,
                                onRefresh = viewModel::refreshMessages,
                                userAvatarUrl = uiState.userAvatarUrl,
                                userName = uiState.userName,
                                endpointIconRes = endpointIconRes(uiState.selectedEndpoint),
                                tintEndpointIcon = isMonochromeEndpointIcon(uiState.selectedEndpoint),
                                streamingSenderName = run {
                                    val model = uiState.selectedModel
                                    if (uiState.selectedEndpoint == EndpointConstants.AGENTS && model != null) {
                                        uiState.agents.find { it.id == model }?.name ?: model
                                    } else {
                                        model ?: "Assistant"
                                    }
                                },
                                showImageDescriptions = showImageDescriptions,
                                chatLayoutStyle = chatLayoutStyle,
                                showAvatars = showAvatars,
                                showBubbles = showBubbles,
                                useKatex = useKatex,
                                searchQuery = if (uiState.isSearchOpen) uiState.searchQuery else null,
                                searchMatchIndices = uiState.searchMatchIndices,
                                currentSearchMatchIndex = uiState.currentSearchMatchIndex,
                                searchScrollToIndex = uiState.searchScrollToIndex,
                                onSearchScrollHandled = viewModel::onSearchScrollHandled,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ChatInput overlays at the bottom so gradient shows content behind
            val isAnyStreaming = uiState.isStreaming ||
                uiState.comparisonState.primaryIsStreaming ||
                uiState.comparisonState.secondaryIsStreaming
            ChatInput(
                inputText = uiState.inputText,
                isStreaming = isAnyStreaming,
                onInputChanged = viewModel::onInputChanged,
                onSend = {
                    viewModel.sendMessage()
                    if (dismissKeyboardOnSend) {
                        keyboardController?.hide()
                    }
                },
                onStop = viewModel::stopGeneration,
                attachedFiles = attachedFiles,
                onFilesSelected = viewModel::onFilesSelected,
                onRemoveFile = viewModel::removeFile,
                promptSuggestions = uiState.availablePrompts,
                onPromptSelected = viewModel::handlePromptMention,
                onSlashCommandSelected = viewModel::handleSlashCommand,
                isRecording = uiState.isRecording,
                isTranscribing = uiState.isTranscribing,
                onStartRecording = onStartRecordingWithPermission,
                onStopRecording = viewModel::stopRecording,
                enabledTools = uiState.effectiveEnabledTools,
                onToggleTool = viewModel::toggleTool,
                mcpServers = uiState.mcpServers,
                selectedMcpServerNames = uiState.selectedMcpServerNames,
                onToggleMcpServer = viewModel::toggleMcpServer,
                onOpenModelParameters = viewModel::showModelParameters,
                onOpenModelSelector = {
                    if (uiState.comparisonState.isEnabled && activeComparisonTab == 1) {
                        showSecondaryModelSheet = true
                    } else {
                        showModelSheet = true
                    }
                },
                selectedModelDisplay = if (uiState.comparisonState.isEnabled && activeComparisonTab == 1) {
                    viewModel.getSecondaryModelDisplayName()
                        ?: uiState.comparisonState.secondaryModel
                        ?: displayModel
                } else {
                    displayModel
                },
                isCodeInterpreterAvailable = uiState.isCodeInterpreterAvailable,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (showPresetPicker) {
        PresetPicker(
            presets = uiState.presets,
            onPresetSelected = { preset ->
                viewModel.loadPreset(preset)
                showPresetPicker = false
            },
            onDismiss = { showPresetPicker = false },
            onEditPreset = { preset ->
                viewModel.loadPreset(preset)
                showPresetPicker = false
            },
            onDeletePreset = { preset ->
                preset.presetId?.let { viewModel.deletePreset(it) }
            },
        )
    }

    if (showSavePresetDialog) {
        SavePresetDialog(
            currentEndpoint = uiState.selectedEndpoint,
            currentModel = uiState.selectedModel,
            onSave = { name ->
                viewModel.savePreset(name)
                showSavePresetDialog = false
            },
            onDismiss = { showSavePresetDialog = false },
        )
    }

    if (uiState.showForkOptionsForMessageId != null) {
        ForkOptionsBottomSheet(
            onDismiss = viewModel::dismissForkOptions,
            onFork = { option, splitAtTarget ->
                viewModel.forkFromMessage(
                    messageId = uiState.showForkOptionsForMessageId!!,
                    option = option,
                    splitAtTarget = splitAtTarget,
                )
            },
        )
    }

    if (uiState.showModelParameters) {
        ModelParameterSheet(
            parameters = uiState.modelParameters,
            onParametersChanged = viewModel::updateModelParameters,
            onDismiss = viewModel::hideModelParameters,
            selectedEndpoint = uiState.selectedEndpoint,
        )
    }

    if (uiState.showRenameDialog) {
        ChatRenameDialog(
            currentTitle = uiState.conversationTitle ?: "",
            onDismiss = viewModel::dismissRenameDialog,
            onConfirm = viewModel::renameConversation,
        )
    }

    if (uiState.showDeleteConfirmation) {
        ChatDeleteConfirmationDialog(
            conversationTitle = uiState.conversationTitle ?: "this conversation",
            onDismiss = viewModel::dismissDeleteConfirmation,
            onConfirm = viewModel::deleteConversation,
        )
    }

    if (showModelSheet) {
        ModelSelectorSheet(
            endpointConfigs = uiState.endpointConfigs,
            availableModels = uiState.availableModels,
            agents = uiState.agents,
            selectedEndpoint = uiState.selectedEndpoint,
            selectedModel = uiState.selectedModel,
            onModelSelected = { endpoint, model ->
                viewModel.onModelSelected(endpoint, model)
                showModelSheet = false
            },
            onDismiss = { showModelSheet = false },
            serverUrl = uiState.serverUrl,
        )
    }

    // Secondary model selector sheet for comparison mode
    if (showSecondaryModelSheet) {
        ModelSelectorSheet(
            endpointConfigs = uiState.endpointConfigs,
            availableModels = uiState.availableModels,
            agents = uiState.agents,
            selectedEndpoint = uiState.comparisonState.secondaryEndpoint,
            selectedModel = uiState.comparisonState.secondaryModel,
            onModelSelected = { endpoint, model ->
                viewModel.setSecondaryModel(endpoint, model)
                showSecondaryModelSheet = false
            },
            onDismiss = { showSecondaryModelSheet = false },
            serverUrl = uiState.serverUrl,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    onLoadPreset: () -> Unit,
    onSavePreset: () -> Unit,
    onOpenDrawer: (() -> Unit)?,
    onOpenSearch: () -> Unit = {},
    onOpenPromptsLibrary: (() -> Unit)? = null,
    isTemporaryChat: Boolean = false,
    onToggleTemporaryChat: () -> Unit = {},
    showTempChatToggle: Boolean = false,
    isComparisonEnabled: Boolean = false,
    onToggleComparison: () -> Unit = {},
    conversationId: String? = null,
    conversationTitle: String? = null,
    sharedLinksEnabled: Boolean = false,
    onShare: () -> Unit = {},
    onRename: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onArchive: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Hamburger menu button to open drawer
        if (onOpenDrawer != null) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open navigation drawer",
                )
            }
        }

        // Show conversation title when viewing an existing conversation
        if (conversationId != null && !conversationTitle.isNullOrBlank()) {
            Text(
                text = conversationTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        if (showTempChatToggle) {
            TempChatToggle(
                isTemporary = isTemporaryChat,
                onToggle = onToggleTemporaryChat,
            )
        }
        Box {
            IconButton(onClick = { showOverflowMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.cd_more_options),
                )
            }
            DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { showOverflowMenu = false },
                shape = RoundedCornerShape(16.dp),
            ) {
                if (conversationId != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_search)) },
                        onClick = {
                            showOverflowMenu = false
                            onOpenSearch()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.load_preset)) },
                    onClick = {
                        showOverflowMenu = false
                        onLoadPreset()
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.FileOpen, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.save_as_preset)) },
                    onClick = {
                        showOverflowMenu = false
                        onSavePreset()
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.SaveAs, contentDescription = null)
                    },
                )
                if (onOpenPromptsLibrary != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.prompts_library)) },
                        onClick = {
                            showOverflowMenu = false
                            onOpenPromptsLibrary()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        },
                    )
                }
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.compare_models),
                                modifier = Modifier.weight(1f),
                            )
                            if (isComparisonEnabled) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.cd_comparison_enabled),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    onClick = {
                        showOverflowMenu = false
                        onToggleComparison()
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Compare, contentDescription = null)
                    },
                )
                if (conversationId != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = conversationTitle ?: "New Chat",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    if (sharedLinksEnabled) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            onClick = {
                                showOverflowMenu = false
                                onShare()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Share, contentDescription = null)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        onClick = {
                            showOverflowMenu = false
                            onRename()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Edit, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_duplicate)) },
                        onClick = {
                            showOverflowMenu = false
                            onDuplicate()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_archive)) },
                        onClick = {
                            showOverflowMenu = false
                            onArchive()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Archive, contentDescription = null)
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
            }
        }
    }

}

@Composable
private fun ChatRenameDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_rename)) },
        text = {
            Column {
                Text(
                    text = "Enter a new title for this conversation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.hint_title)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title) },
                enabled = title.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ChatDeleteConfirmationDialog(
    conversationTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_delete_conversation)) },
        text = {
            Text(
                text = "Are you sure you want to delete \"$conversationTitle\"? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * Maps the user-facing STT engine name (from settings) to an Android package name
 * that can be set on the device speech recognition intent. Returns null for "Default",
 * empty/unknown values, or "Whisper" (which uses server-side STT, not a device package).
 *
 * Note: "Whisper" is handled separately in [onStartRecordingWithPermission] above --
 * it routes through the server STT path (record audio + upload) rather than the device
 * speech recognizer. This function is only called for the device recognizer path.
 */
private fun mapSttEngineToPackage(engine: String): String? = when (engine.lowercase()) {
    "google" -> "com.google.android.googlequicksearchbox"
    "whisper" -> null // Server-side engine; never reaches device recognizer path
    "device" -> null // Explicit on-device; uses system default speech recognizer
    "default", "" -> null
    else -> null
}

/**
 * Maps the user-facing STT language name (from settings) to a BCP-47 locale tag
 * for [RecognizerIntent.EXTRA_LANGUAGE]. Returns null for "Auto-detect" or
 * empty values, which lets the recognizer use the device default.
 */
private fun mapSttLanguageToLocale(language: String): String? = when (language.lowercase()) {
    "english" -> "en-US"
    "spanish" -> "es-ES"
    "french" -> "fr-FR"
    "german" -> "de-DE"
    "japanese" -> "ja-JP"
    "chinese" -> "zh-CN"
    "auto-detect", "" -> null
    else -> null
}

/**
 * Replaces the parallel response message's content with [finalContent] captured from
 * the streaming buffer. The server-loaded message may only contain the primary agent's
 * content, so for the secondary pane we substitute the captured text.
 * Also updates the sender name so the bubble shows the correct model.
 */
private fun buildComparisonDisplayMessages(
    displayMessages: List<MessageNode>,
    parallelMessageId: String?,
    finalContent: String?,
    senderName: String?,
): List<MessageNode> {
    if (parallelMessageId == null || finalContent.isNullOrBlank()) return displayMessages
    return displayMessages.map { node ->
        if (node.message.messageId == parallelMessageId) {
            // Substitute the parallel message content with the captured final content for this pane
            node.copy(
                message = node.message.copy(
                    content = listOf(
                        MessageContentPart(type = ContentType.TEXT, text = finalContent),
                    ),
                    sender = senderName ?: node.message.sender,
                ),
            )
        } else {
            node
        }
    }
}

