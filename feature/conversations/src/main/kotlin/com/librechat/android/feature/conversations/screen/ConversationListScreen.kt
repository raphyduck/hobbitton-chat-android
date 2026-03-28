package com.librechat.android.feature.conversations.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.librechat.android.core.model.Conversation
import com.librechat.android.core.ui.components.EmptyState
import com.librechat.android.core.ui.components.ErrorBanner
import com.librechat.android.core.ui.components.LoadingIndicator
import com.librechat.android.feature.conversations.components.ConversationActions
import com.librechat.android.feature.conversations.components.ConversationItem
import com.librechat.android.feature.conversations.components.ConversationSearchBar
import com.librechat.android.feature.conversations.components.DateGroupHeader
import com.librechat.android.feature.conversations.components.TagFilterBar
import com.librechat.android.feature.conversations.components.TagPicker
import com.librechat.android.feature.conversations.export.ExportFormat
import com.librechat.android.feature.conversations.export.ExportFormatPicker
import com.librechat.android.feature.conversations.viewmodel.ConversationListEvent
import com.librechat.android.feature.conversations.viewmodel.ConversationListViewModel
import com.librechat.android.feature.conversations.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNewChatClick: () -> Unit = {},
    onNavigateToArchived: () -> Unit = {},
    viewModel: ConversationListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedConversation by remember { mutableStateOf<Conversation?>(null) }
    var showTagPicker by remember { mutableStateOf(false) }
    var tagPickerConversation by remember { mutableStateOf<Conversation?>(null) }
    var showExportFormatPicker by remember { mutableStateOf(false) }
    var exportConversation by remember { mutableStateOf<Conversation?>(null) }
    var pendingExportContent by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val copyOfPrefix = stringResource(R.string.copy_of)
    val newChatLabel = stringResource(R.string.new_chat)

    // SAF launcher for saving exported file
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        if (uri != null && pendingExportContent != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(pendingExportContent!!.toByteArray())
                }
                Toast.makeText(context, context.getString(R.string.conversation_exported), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.export_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
        pendingExportContent = null
    }

    // SAF launcher for importing a JSON file
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                val jsonContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                }
                if (jsonContent != null) {
                    viewModel.importConversation(jsonContent)
                }
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.import_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Collect one-shot events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ConversationListEvent.ShareLinkCopied -> {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("Share Link", event.url))
                    snackbarHostState.showSnackbar(context.getString(R.string.link_copied))
                }
                is ConversationListEvent.NavigateToConversation -> {
                    onConversationClick(event.conversationId)
                }
                is ConversationListEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is ConversationListEvent.ExportReady -> {
                    pendingExportContent = event.content
                    val ext = when (event.format) {
                        ExportFormat.JSON -> "json"
                        ExportFormat.MARKDOWN -> "md"
                    }
                    val safeTitle = event.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    exportFileLauncher.launch("$safeTitle.$ext")
                }
                is ConversationListEvent.ImportSuccess -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.imported_title, event.title))
                }
            }
        }
    }

    val listState = rememberLazyListState()

    // Detect when we scroll near the end to trigger load more
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleIndex >= totalItems - 3 && uiState.hasMore && !uiState.isLoading
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { shouldLoadMore }
            .collect { shouldLoad ->
                if (shouldLoad) {
                    viewModel.loadMore()
                }
            }
    }

    val groupedConversations = uiState.groupedConversations

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChatClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.cd_new_chat),
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search bar with archive button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ConversationSearchBar(
                        query = uiState.searchQuery,
                        onQueryChanged = viewModel::onSearchQueryChanged,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onNavigateToArchived) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = stringResource(R.string.cd_archived_conversations),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Tag filter bar
                TagFilterBar(
                    tags = uiState.tags,
                    selectedTags = uiState.selectedTags,
                    onTagToggle = viewModel::toggleTag,
                    onClearFilter = viewModel::clearTagFilter,
                )

                when {
                    // Initial loading state
                    uiState.isLoading && uiState.conversationCount == 0 -> {
                        LoadingIndicator()
                    }

                    // Searching state
                    uiState.isSearching -> {
                        LoadingIndicator()
                    }

                    // Error state when no conversations loaded
                    !uiState.isLoading && uiState.conversationCount == 0 && uiState.error != null -> {
                        ErrorBanner(
                            message = uiState.error ?: stringResource(R.string.could_not_load_conversations),
                            onRetry = {
                                viewModel.dismissError()
                                viewModel.loadConversations()
                            },
                        )
                    }

                    // Empty state (not loading, no conversations)
                    !uiState.isLoading && uiState.conversationCount == 0 -> {
                        EmptyState(
                            title = if (uiState.searchQuery.isNotEmpty()) {
                                stringResource(R.string.no_results_found)
                            } else {
                                stringResource(R.string.no_conversations_yet)
                            },
                            description = if (uiState.searchQuery.isNotEmpty()) {
                                stringResource(R.string.try_different_search)
                            } else {
                                stringResource(R.string.start_new_chat)
                            },
                            icon = Icons.Default.Forum,
                        )
                    }

                    // Content state
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            // Error banner at top if present
                            if (uiState.error != null) {
                                item(key = "error") {
                                    ErrorBanner(
                                        message = uiState.error ?: stringResource(R.string.unknown_error),
                                        onRetry = {
                                            viewModel.dismissError()
                                            viewModel.loadConversations()
                                        },
                                    )
                                }
                            }

                            groupedConversations.forEach { (dateGroup, displayItems) ->
                                // Date group header
                                item(key = "header_$dateGroup") {
                                    DateGroupHeader(label = dateGroup)
                                }

                                // Conversation items in this group
                                items(
                                    items = displayItems,
                                    key = { it.conversationId },
                                    contentType = { "conversation" },
                                ) { displayData ->
                                    ConversationItem(
                                        data = displayData,
                                        onClick = {
                                            onConversationClick(displayData.conversationId)
                                        },
                                        onActionsClick = {
                                            selectedConversation = viewModel.getConversation(
                                                displayData.conversationId,
                                            )
                                        },
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 52.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }

                            // Loading indicator at bottom when loading more
                            if (uiState.isLoading && uiState.conversationCount > 0) {
                                item(key = "loading_more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom sheet for conversation actions
    if (selectedConversation != null) {
        ConversationActions(
            conversation = selectedConversation!!,
            onDismiss = { selectedConversation = null },
            isBookmarked = selectedConversation?.conversationId?.let {
                viewModel.isBookmarked(it)
            } ?: false,
            onBookmarkToggle = {
                selectedConversation?.conversationId?.let { id ->
                    viewModel.toggleBookmark(id)
                }
                selectedConversation = null
            },
            onRename = { newTitle ->
                selectedConversation?.conversationId?.let { id ->
                    viewModel.renameConversation(id, newTitle)
                }
                selectedConversation = null
            },
            onArchive = {
                selectedConversation?.conversationId?.let { id ->
                    viewModel.archiveConversation(id)
                }
                selectedConversation = null
            },
            onDelete = {
                selectedConversation?.conversationId?.let { id ->
                    viewModel.deleteConversation(id)
                }
                selectedConversation = null
            },
            onTags = {
                tagPickerConversation = selectedConversation
                showTagPicker = true
                selectedConversation = null
            },
            onShare = {
                selectedConversation?.conversationId?.let { id ->
                    viewModel.shareConversation(id)
                }
                selectedConversation = null
            },
            onFork = {
                // Fork is a message-level action (requires a specific messageId).
                // Use Duplicate for conversation-level copying.
                selectedConversation = null
            },
            onDuplicate = {
                val convo = selectedConversation
                convo?.conversationId?.let { id ->
                    viewModel.duplicateConversation(id, copyOfPrefix.format(convo.title ?: newChatLabel))
                }
                selectedConversation = null
            },
            onExport = {
                exportConversation = selectedConversation
                showExportFormatPicker = true
                selectedConversation = null
            },
        )
    }

    // Tag picker bottom sheet
    if (showTagPicker && tagPickerConversation != null) {
        TagPicker(
            availableTags = uiState.tags,
            currentTags = tagPickerConversation?.tags ?: emptyList(),
            onTagsChanged = { newTags ->
                tagPickerConversation?.conversationId?.let { id ->
                    viewModel.updateConversationTags(id, newTags)
                }
            },
            onDismiss = {
                showTagPicker = false
                tagPickerConversation = null
            },
        )
    }

    // Export format picker
    if (showExportFormatPicker && exportConversation != null) {
        ExportFormatPicker(
            onFormatSelected = { format ->
                exportConversation?.conversationId?.let { id ->
                    viewModel.exportConversation(id, exportConversation?.title, format)
                }
                showExportFormatPicker = false
                exportConversation = null
            },
            onDismiss = {
                showExportFormatPicker = false
                exportConversation = null
            },
        )
    }
}
