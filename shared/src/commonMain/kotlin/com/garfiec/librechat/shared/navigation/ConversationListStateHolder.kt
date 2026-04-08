package com.garfiec.librechat.shared.navigation

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.extensions.toInstantOrNull
import com.garfiec.librechat.core.common.extensions.toRelativeDateGroup
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.model.Conversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class ConversationListStateHolder(
    private val conversationRepository: ConversationRepository,
    private val scope: CoroutineScope,
) {

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    private val _recentConversations = MutableStateFlow<List<Conversation>>(emptyList())
    val recentConversations: StateFlow<List<Conversation>> = _recentConversations.asStateFlow()

    private val _groupedConversations = MutableStateFlow<List<Pair<String, List<Conversation>>>>(emptyList())
    val groupedConversations: StateFlow<List<Pair<String, List<Conversation>>>> = _groupedConversations.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var nextCursor: String? = null

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        observeConversations()
        observeSearchQuery()
        loadInitialConversations()
    }

    private fun observeConversations() {
        scope.launch {
            try {
                conversationRepository.observeConversations().collect { result ->
                    if (result is Result.Success) {
                        _recentConversations.value = result.data
                        if (_searchQuery.value.isBlank()) {
                            _groupedConversations.value = groupConversationsByDate(result.data)
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.w(e) { "Failed to observe conversations" }
            }
        }
    }

    private fun loadInitialConversations() {
        scope.launch {
            loadPage(cursor = null)
        }
    }

    private suspend fun loadPage(cursor: String?): Boolean {
        return try {
            val result = conversationRepository.loadNextPage(cursor = cursor)
            if (result is Result.Success) {
                nextCursor = result.data
                _hasMore.value = result.data != null
                true
            } else {
                Logger.w { "Failed to load conversations page" }
                false
            }
        } catch (e: Exception) {
            Logger.w(e) { "Failed to load conversations page" }
            false
        }
    }

    fun loadMoreConversations() {
        if (_isLoadingMore.value || !_hasMore.value) return
        _isLoadingMore.value = true
        scope.launch {
            loadPage(cursor = nextCursor)
            _isLoadingMore.value = false
        }
    }

    fun refreshConversations() {
        _isRefreshing.value = true
        scope.launch {
            nextCursor = null
            _hasMore.value = true
            loadPage(cursor = null)
            _isRefreshing.value = false
        }
    }

    fun setActiveConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _groupedConversations.value = groupConversationsByDate(_recentConversations.value)
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        scope.launch {
            _searchQuery
                .filter { it.isNotBlank() }
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { query ->
                    val filtered = _recentConversations.value.filter { conversation ->
                        conversation.title?.contains(query, ignoreCase = true) == true
                    }
                    _groupedConversations.value = groupConversationsByDate(filtered)
                }
        }
    }

    fun reset() {
        _recentConversations.value = emptyList()
        _groupedConversations.value = emptyList()
        _activeConversationId.value = null
        _searchQuery.value = ""
    }

    private fun groupConversationsByDate(
        conversations: List<Conversation>,
    ): List<Pair<String, List<Conversation>>> {
        if (conversations.isEmpty()) return emptyList()
        return conversations
            .groupBy { conversation ->
                conversation.updatedAt
                    ?.toInstantOrNull()
                    ?.toRelativeDateGroup()
                    ?: "Unknown"
            }
            .toList()
    }
}
