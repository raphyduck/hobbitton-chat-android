package com.garfiec.librechat.shared.navigation

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.model.Conversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FavoritesStateHolder(
    private val settingsDataStore: SettingsDataStore,
    private val recentConversations: StateFlow<List<Conversation>>,
    private val scope: CoroutineScope,
) {

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _favoriteConversations = MutableStateFlow<List<Conversation>>(emptyList())
    val favoriteConversations: StateFlow<List<Conversation>> = _favoriteConversations.asStateFlow()

    init {
        observeBookmarks()
        observeRecentConversations()
    }

    private fun observeBookmarks() {
        scope.launch {
            try {
                settingsDataStore.bookmarkedConversationIds.collect { bookmarkedIds ->
                    _favorites.value = bookmarkedIds
                    updateFavoriteConversations()
                }
            } catch (e: Exception) {
                Logger.w(e) { "Failed to observe bookmarks" }
            }
        }
    }

    private fun observeRecentConversations() {
        scope.launch {
            recentConversations.collect {
                updateFavoriteConversations()
            }
        }
    }

    fun toggleFavorite(conversationId: String) {
        scope.launch {
            settingsDataStore.toggleBookmark(conversationId)
        }
    }

    private fun updateFavoriteConversations() {
        val bookmarkedIds = _favorites.value
        val allConversations = recentConversations.value
        _favoriteConversations.value = allConversations.filter { conversation ->
            conversation.conversationId in bookmarkedIds ||
                conversation.tags.any {
                    it.equals("favorite", ignoreCase = true) ||
                        it.equals("bookmark", ignoreCase = true)
                }
        }
    }

    fun reset() {
        _favorites.value = emptySet()
        _favoriteConversations.value = emptyList()
    }
}
