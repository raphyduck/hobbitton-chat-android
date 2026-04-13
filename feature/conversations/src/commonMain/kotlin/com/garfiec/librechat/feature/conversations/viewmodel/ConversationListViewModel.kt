package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.extensions.toInstantOrNull
import com.garfiec.librechat.core.common.extensions.toRelativeDateGroup
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.SearchRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.feature.conversations.components.ConversationDisplayData
import com.garfiec.librechat.feature.conversations.components.toDisplayData
import com.garfiec.librechat.feature.conversations.export.ConversationExporter
import com.garfiec.librechat.feature.conversations.export.ConversationImporter
import com.garfiec.librechat.feature.conversations.export.ExportFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class ConversationListUiState(
    val groupedConversations: List<Pair<String, List<ConversationDisplayData>>> = emptyList(),
    val conversationCount: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val hasMore: Boolean = true,
    val tags: List<ConversationTag> = emptyList(),
    val selectedTags: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
)

sealed interface ConversationListEvent {
    data class ShareLinkCopied(val url: String) : ConversationListEvent
    data class NavigateToConversation(val conversationId: String) : ConversationListEvent
    data class ShowError(val message: String) : ConversationListEvent
    data class ExportReady(val content: String, val format: ExportFormat, val title: String) : ConversationListEvent
    data class ImportSuccess(val title: String) : ConversationListEvent
}

class ConversationListViewModel(
    private val conversationRepository: ConversationRepository,
    private val tagRepository: TagRepository,
    private val searchRepository: SearchRepository,
    private val shareRepository: ShareRepository,
    private val conversationExporter: ConversationExporter,
    private val conversationImporter: ConversationImporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ConversationListEvent>()
    val events: SharedFlow<ConversationListEvent> = _events.asSharedFlow()

    /** Raw conversations kept for internal lookups (action sheets, etc.). */
    private var conversations: List<Conversation> = emptyList()
    private var searchJob: Job? = null

    init {
        loadConversations()
        observeConversations()
        observeTags()
    }

    private fun observeConversations() {
        viewModelScope.launch {
            conversationRepository.observeConversations().collect { result ->
                when (result) {
                    is Result.Success -> {
                        // Only update from Room when not in search mode
                        if (_uiState.value.searchQuery.isEmpty()) {
                            conversations = result.data
                            recomputeGroupedConversations()
                            _uiState.value = _uiState.value.copy(isLoading = false)
                        }
                    }
                    is Result.Error -> {
                        _uiState.value = _uiState.value.copy(
                            error = result.message,
                            isLoading = false,
                        )
                    }
                    is Result.Loading -> { /* no-op */ }
                }
            }
        }
    }

    private fun observeTags() {
        viewModelScope.launch {
            tagRepository.observeTags().collect { tags ->
                _uiState.value = _uiState.value.copy(
                    tags = tags.filter { it.count > 0 && it.tag != SAVED_TAG },
                )
            }
        }
    }

    private fun recomputeGroupedConversations() {
        val grouped = groupConversationsByDate(conversations)
        _uiState.value = _uiState.value.copy(
            groupedConversations = grouped,
            conversationCount = conversations.size,
        )
    }

    fun getConversation(conversationId: String): Conversation? =
        conversations.firstOrNull { it.conversationId == conversationId }

    fun toggleFavorite(conversation: Conversation) {
        val id = conversation.conversationId ?: return
        viewModelScope.launch {
            when (val result = tagRepository.toggleFavorite(id, conversation.tags)) {
                is Result.Error -> {
                    _events.emit(
                        ConversationListEvent.ShowError(
                            result.message ?: "Failed to update favorite",
                        ),
                    )
                }
                is Result.Success -> { /* no-op */ }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun loadConversations() {
        val activeTags = _uiState.value.selectedTags.toList().ifEmpty { null }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = conversationRepository.loadNextPage(null, tags = activeTags)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        nextCursor = result.data,
                        hasMore = result.data != null,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message,
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun loadMore() {
        val cursor = _uiState.value.nextCursor ?: return
        if (_uiState.value.isLoading) return

        val activeTags = _uiState.value.selectedTags.toList().ifEmpty { null }
        viewModelScope.launch {
            when (val result = conversationRepository.loadNextPage(cursor, tags = activeTags)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        nextCursor = result.data,
                        hasMore = result.data != null,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val activeTags = _uiState.value.selectedTags.toList().ifEmpty { null }
            coroutineScope {
                launch {
                    when (val result = conversationRepository.loadNextPage(null, tags = activeTags)) {
                        is Result.Success -> {
                            _uiState.value = _uiState.value.copy(
                                nextCursor = result.data,
                                hasMore = result.data != null,
                            )
                        }
                        is Result.Error -> {
                            _uiState.value = _uiState.value.copy(error = result.message)
                        }
                        is Result.Loading -> { /* no-op */ }
                    }
                }
                launch { tagRepository.refreshTags() }
                launch { conversationRepository.syncFavoritesFromServer() }
            }
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun toggleTag(tag: String) {
        val current = _uiState.value.selectedTags
        val updated = if (tag in current) current - tag else current + tag
        _uiState.value = _uiState.value.copy(selectedTags = updated)
        // Reload conversations with new tag filter
        loadConversations()
    }

    fun clearTagFilter() {
        _uiState.value = _uiState.value.copy(selectedTags = emptySet())
        loadConversations()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(isSearching = false)
            // Reload normal conversation list
            loadConversations()
            return
        }

        searchJob = viewModelScope.launch {
            delay(500) // 500ms debounce
            _uiState.value = _uiState.value.copy(isSearching = true)
            when (val result = searchRepository.search(query)) {
                is Result.Success -> {
                    conversations = result.data
                    recomputeGroupedConversations()
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        hasMore = false,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        error = result.message,
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun updateConversationTags(conversation: Conversation, userTags: List<String>) {
        val id = conversation.conversationId ?: return
        val wasFavorited = SAVED_TAG in conversation.tags
        val cleaned = userTags.filterNot { it == SAVED_TAG }
        val finalTags = if (wasFavorited) cleaned + SAVED_TAG else cleaned
        viewModelScope.launch {
            when (tagRepository.setConversationTags(id, finalTags)) {
                is Result.Success -> {
                    // Room Flow already emits the updated conversation via
                    // observeConversations; we only need fresh tag counts.
                    tagRepository.refreshTags()
                }
                is Result.Error -> {
                    _events.emit(ConversationListEvent.ShowError("Failed to update tags"))
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            try {
                conversationRepository.updateTitle(id, newTitle)
            } catch (e: Exception) {
                Logger.e(e) { "Failed to rename conversation" }
                _events.emit(ConversationListEvent.ShowError("Failed to rename conversation"))
            }
        }
    }

    fun archiveConversation(id: String) {
        viewModelScope.launch {
            try {
                conversationRepository.archive(id, true)
            } catch (e: Exception) {
                Logger.e(e) { "Failed to archive conversation" }
                _events.emit(ConversationListEvent.ShowError("Failed to archive conversation"))
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            try {
                conversationRepository.delete(id)
            } catch (e: Exception) {
                Logger.e(e) { "Failed to delete conversation" }
                _events.emit(ConversationListEvent.ShowError("Failed to delete conversation"))
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun shareConversation(conversationId: String) {
        viewModelScope.launch {
            when (val result = shareRepository.createShareLink(conversationId)) {
                is Result.Success -> {
                    _events.emit(ConversationListEvent.ShareLinkCopied(result.data))
                }
                is Result.Error -> {
                    _events.emit(
                        ConversationListEvent.ShowError(result.message ?: "Failed to create share link"),
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun forkConversation(conversationId: String, messageId: String) {
        viewModelScope.launch {
            when (val result = conversationRepository.forkConversation(conversationId, messageId)) {
                is Result.Success -> {
                    result.data.conversationId?.let { newId ->
                        _events.emit(ConversationListEvent.NavigateToConversation(newId))
                    }
                }
                is Result.Error -> {
                    _events.emit(
                        ConversationListEvent.ShowError(result.message ?: "Failed to fork conversation"),
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun duplicateConversation(conversationId: String, title: String?) {
        viewModelScope.launch {
            when (val result = conversationRepository.duplicateConversation(conversationId, title)) {
                is Result.Success -> {
                    result.data.conversationId?.let { newId ->
                        _events.emit(ConversationListEvent.NavigateToConversation(newId))
                    }
                }
                is Result.Error -> {
                    _events.emit(
                        ConversationListEvent.ShowError(result.message ?: "Failed to duplicate conversation"),
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun exportConversation(conversationId: String, title: String?, format: ExportFormat) {
        viewModelScope.launch {
            val result = when (format) {
                ExportFormat.JSON -> conversationExporter.exportAsJson(conversationId)
                ExportFormat.MARKDOWN -> conversationExporter.exportAsMarkdown(conversationId)
            }
            when (result) {
                is Result.Success -> {
                    _events.emit(
                        ConversationListEvent.ExportReady(
                            content = result.data,
                            format = format,
                            title = title ?: "conversation",
                        ),
                    )
                }
                is Result.Error -> {
                    _events.emit(
                        ConversationListEvent.ShowError(result.message ?: "Failed to export conversation"),
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun importConversation(jsonContent: String) {
        viewModelScope.launch {
            // First validate the JSON format
            when (val parseResult = conversationImporter.parseJson(jsonContent)) {
                is Result.Success -> {
                    val export = parseResult.data
                    // Now upload to server
                    when (val importResult = conversationRepository.importConversation(jsonContent)) {
                        is Result.Success -> {
                            _events.emit(
                                ConversationListEvent.ImportSuccess(
                                    title = export.conversation.title ?: "Imported conversation",
                                ),
                            )
                            refresh()
                        }
                        is Result.Error -> {
                            _events.emit(
                                ConversationListEvent.ShowError(
                                    importResult.message ?: "Failed to upload conversation to server",
                                ),
                            )
                        }
                        is Result.Loading -> { /* no-op */ }
                    }
                }
                is Result.Error -> {
                    _events.emit(
                        ConversationListEvent.ShowError(parseResult.message ?: "Failed to parse conversation file"),
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    companion object {
        private fun groupConversationsByDate(
            conversations: List<Conversation>,
        ): List<Pair<String, List<ConversationDisplayData>>> {
            if (conversations.isEmpty()) return emptyList()
            return conversations
                .groupBy { conversation ->
                    conversation.updatedAt
                        ?.toInstantOrNull()
                        ?.toRelativeDateGroup()
                        ?: "Unknown"
                }
                .map { (group, convos) ->
                    group to convos.map { it.toDisplayData() }
                }
        }
    }
}
