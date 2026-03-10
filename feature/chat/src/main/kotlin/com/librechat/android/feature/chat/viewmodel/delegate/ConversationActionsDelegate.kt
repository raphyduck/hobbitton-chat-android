package com.librechat.android.feature.chat.viewmodel.delegate

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.data.repository.ConversationRepository
import com.librechat.android.core.data.repository.ShareRepository
import com.librechat.android.feature.chat.viewmodel.ChatStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConversationActionsDelegate(
    private val stateHandle: ChatStateHandle,
    private val conversationRepository: ConversationRepository,
    private val shareRepository: ShareRepository,
) {

    private val _shareLinkUrl = MutableStateFlow<String?>(null)
    val shareLinkUrl: StateFlow<String?> = _shareLinkUrl.asStateFlow()

    fun showRenameDialog() {
        stateHandle.update { copy(showRenameDialog = true) }
    }

    fun dismissRenameDialog() {
        stateHandle.update { copy(showRenameDialog = false) }
    }

    fun renameConversation(newTitle: String) {
        val conversationId = stateHandle.state.conversationId ?: return
        stateHandle.update { copy(showRenameDialog = false) }
        stateHandle.scope.launch {
            when (conversationRepository.updateTitle(conversationId, newTitle)) {
                is Result.Success -> {
                    stateHandle.update { copy(conversationTitle = newTitle) }
                }
                is Result.Error -> {
                    stateHandle.update { copy(error = "Failed to rename conversation") }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun showDeleteConfirmation() {
        stateHandle.update { copy(showDeleteConfirmation = true) }
    }

    fun dismissDeleteConfirmation() {
        stateHandle.update { copy(showDeleteConfirmation = false) }
    }

    fun deleteConversation() {
        val conversationId = stateHandle.state.conversationId ?: return
        stateHandle.update { copy(showDeleteConfirmation = false) }
        stateHandle.scope.launch {
            when (conversationRepository.delete(conversationId)) {
                is Result.Success -> {
                    // Signal navigation back by clearing the conversation
                    stateHandle.update { copy(conversationId = null) }
                }
                is Result.Error -> {
                    stateHandle.update { copy(error = "Failed to delete conversation") }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun archiveConversation() {
        val conversationId = stateHandle.state.conversationId ?: return
        stateHandle.scope.launch {
            when (conversationRepository.archive(conversationId, true)) {
                is Result.Success -> {
                    // Signal navigation back by clearing the conversation
                    stateHandle.update { copy(conversationId = null) }
                }
                is Result.Error -> {
                    stateHandle.update { copy(error = "Failed to archive conversation") }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun duplicateConversation() {
        val conversationId = stateHandle.state.conversationId ?: return
        val title = stateHandle.state.conversationTitle
        stateHandle.scope.launch {
            when (val result = conversationRepository.duplicateConversation(conversationId, title)) {
                is Result.Success -> {
                    stateHandle.update { copy(duplicatedConversationId = result.data.conversationId) }
                }
                is Result.Error -> {
                    stateHandle.update { copy(error = "Failed to duplicate conversation") }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun onDuplicatedConversationHandled() {
        stateHandle.update { copy(duplicatedConversationId = null) }
    }

    fun shareConversation() {
        val conversationId = stateHandle.state.conversationId ?: return
        stateHandle.scope.launch {
            when (val result = shareRepository.createShareLink(conversationId)) {
                is Result.Success -> {
                    stateHandle.update { copy(error = null) }
                    // Store the share URL to be copied by the UI
                    _shareLinkUrl.value = result.data
                }
                is Result.Error -> {
                    stateHandle.update { copy(error = result.message ?: "Failed to create share link") }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun onShareLinkHandled() {
        _shareLinkUrl.value = null
    }

    fun showForkOptions(messageId: String) {
        stateHandle.update { copy(showForkOptionsForMessageId = messageId) }
    }

    fun dismissForkOptions() {
        stateHandle.update { copy(showForkOptionsForMessageId = null) }
    }

    fun forkFromMessage(messageId: String, option: String, splitAtTarget: Boolean = false) {
        val conversationId = stateHandle.state.conversationId ?: return
        val latestMessageId = stateHandle.state.displayMessages.lastOrNull()?.message?.messageId
        stateHandle.update {
            copy(
                showForkOptionsForMessageId = null,
                isForkInProgress = true,
            )
        }
        stateHandle.scope.launch {
            val result = conversationRepository.forkConversation(
                conversationId = conversationId,
                messageId = messageId,
                option = option,
                splitAtTarget = if (splitAtTarget) true else null,
                latestMessageId = if (splitAtTarget) latestMessageId else null,
            )
            when (result) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(
                            forkedConversationId = result.data.conversationId,
                            isForkInProgress = false,
                        )
                    }
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            error = result.message ?: "Could not fork conversation",
                            isForkInProgress = false,
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun onForkedConversationHandled() {
        stateHandle.update { copy(forkedConversationId = null) }
    }
}
