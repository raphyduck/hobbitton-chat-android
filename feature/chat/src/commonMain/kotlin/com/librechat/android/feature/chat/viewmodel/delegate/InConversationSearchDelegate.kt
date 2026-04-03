package com.librechat.android.feature.chat.viewmodel.delegate

import com.librechat.android.feature.chat.viewmodel.ChatStateHandle
import com.librechat.android.feature.chat.viewmodel.SearchMatch

class InConversationSearchDelegate(
    private val stateHandle: ChatStateHandle,
) {

    fun openSearch() {
        stateHandle.update {
            copy(
                isSearchOpen = true,
                searchQuery = "",
                searchMatchIndices = emptyList(),
                currentSearchMatchIndex = 0,
                searchScrollToIndex = null,
            )
        }
    }

    fun closeSearch() {
        stateHandle.update {
            copy(
                isSearchOpen = false,
                searchQuery = "",
                searchMatchIndices = emptyList(),
                currentSearchMatchIndex = 0,
                searchScrollToIndex = null,
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        val state = stateHandle.state
        if (query.isBlank()) {
            stateHandle.update {
                copy(
                    searchQuery = query,
                    searchMatchIndices = emptyList(),
                    currentSearchMatchIndex = 0,
                    searchScrollToIndex = null,
                )
            }
            return
        }

        val lowerQuery = query.lowercase()
        val allMatches = mutableListOf<SearchMatch>()

        state.displayMessages.forEachIndexed { index, node ->
            val message = node.message
            val contentParts = message.content
            val text = if (!contentParts.isNullOrEmpty()) {
                contentParts.mapNotNull { part -> part.text ?: part.think }.joinToString("")
            } else {
                message.text
            }
            // Count occurrences in this message
            val lowerText = text.lowercase()
            var startIndex = 0
            var occurrenceInMessage = 0
            while (true) {
                val foundIndex = lowerText.indexOf(lowerQuery, startIndex)
                if (foundIndex < 0) break
                allMatches.add(SearchMatch(messageIndex = index, occurrenceInMessage = occurrenceInMessage))
                occurrenceInMessage++
                startIndex = foundIndex + query.length
            }
        }

        stateHandle.update {
            copy(
                searchQuery = query,
                searchMatchIndices = allMatches,
                currentSearchMatchIndex = 0,
                searchScrollToIndex = allMatches.firstOrNull()?.messageIndex,
            )
        }
    }

    fun nextSearchMatch() {
        val state = stateHandle.state
        if (state.searchMatchIndices.isEmpty()) return
        val nextIndex = (state.currentSearchMatchIndex + 1) % state.searchMatchIndices.size
        stateHandle.update {
            copy(
                currentSearchMatchIndex = nextIndex,
                searchScrollToIndex = searchMatchIndices[nextIndex].messageIndex,
            )
        }
    }

    fun previousSearchMatch() {
        val state = stateHandle.state
        if (state.searchMatchIndices.isEmpty()) return
        val prevIndex = if (state.currentSearchMatchIndex > 0) {
            state.currentSearchMatchIndex - 1
        } else {
            state.searchMatchIndices.size - 1
        }
        stateHandle.update {
            copy(
                currentSearchMatchIndex = prevIndex,
                searchScrollToIndex = searchMatchIndices[prevIndex].messageIndex,
            )
        }
    }

    fun onSearchScrollHandled() {
        stateHandle.update { copy(searchScrollToIndex = null) }
    }
}
