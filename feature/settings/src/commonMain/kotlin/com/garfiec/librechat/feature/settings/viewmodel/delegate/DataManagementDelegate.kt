package com.garfiec.librechat.feature.settings.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.model.SharedLink
import com.garfiec.librechat.feature.settings.model.SharedLinkDisplayData
import com.garfiec.librechat.feature.settings.util.PlatformCacheCleaner
import com.garfiec.librechat.feature.settings.viewmodel.SettingsStateHandle
import kotlinx.coroutines.launch

/**
 * Handles clear conversations, export, shared links, cache clearing, and key revocation.
 */
class DataManagementDelegate(
    private val stateHandle: SettingsStateHandle,
    private val cacheCleaner: PlatformCacheCleaner,
    private val conversationRepository: ConversationRepository,
    private val shareRepository: ShareRepository,
    private val keyRepository: KeyRepository,
) {

    fun clearAllChats() {
        stateHandle.scope.launch {
            stateHandle.update { copy(isClearing = true) }
            when (val result = conversationRepository.deleteAll()) {
                is Result.Success -> {
                    stateHandle.update { copy(isClearing = false) }
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            isClearing = false,
                            error = result.message ?: "Failed to clear conversations",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun exportAllData() {
        stateHandle.update { copy(showExportComingSoon = true) }
    }

    fun dismissExportComingSoon() {
        stateHandle.update { copy(showExportComingSoon = false) }
    }

    fun loadSharedLinks() {
        stateHandle.scope.launch {
            stateHandle.update { copy(isSharedLinksLoading = true) }
            when (val result = shareRepository.getSharedLinksPaginated()) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(
                            sharedLinks = result.data.links.map { it.toDisplayData() },
                            sharedLinksNextCursor = result.data.nextCursor,
                            sharedLinksHasNextPage = result.data.hasNextPage ?: false,
                            isSharedLinksLoading = false,
                        )
                    }
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            isSharedLinksLoading = false,
                            error = result.message ?: "Failed to load shared links",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun loadMoreSharedLinks() {
        val cursor = stateHandle.state.sharedLinksNextCursor ?: return
        stateHandle.scope.launch {
            stateHandle.update { copy(isSharedLinksLoading = true) }
            when (val result = shareRepository.getSharedLinksPaginated(cursor = cursor)) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(
                            sharedLinks = sharedLinks + result.data.links.map { it.toDisplayData() },
                            sharedLinksNextCursor = result.data.nextCursor,
                            sharedLinksHasNextPage = result.data.hasNextPage ?: false,
                            isSharedLinksLoading = false,
                        )
                    }
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            isSharedLinksLoading = false,
                            error = result.message ?: "Failed to load more shared links",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun toggleSharedLinkVisibility(shareId: String) {
        stateHandle.scope.launch {
            when (val result = shareRepository.toggleShareVisibility(shareId)) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(
                            sharedLinks = sharedLinks.map { link ->
                                if (link.shareId == shareId) result.data.toDisplayData() else link
                            },
                        )
                    }
                }
                is Result.Error -> {
                    stateHandle.update { copy(error = result.message ?: "Failed to toggle visibility") }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun deleteSharedLink(shareId: String) {
        stateHandle.scope.launch {
            when (val result = shareRepository.deleteShareLink(shareId)) {
                is Result.Success -> {
                    stateHandle.update {
                        copy(sharedLinks = sharedLinks.filter { it.shareId != shareId })
                    }
                }
                is Result.Error -> {
                    stateHandle.update { copy(error = result.message ?: "Failed to delete shared link") }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun clearCache() {
        stateHandle.scope.launch {
            stateHandle.update { copy(isCacheClearing = true) }
            try {
                cacheCleaner.clearCache()
                stateHandle.update { copy(isCacheClearing = false) }
            } catch (e: Exception) {
                stateHandle.update {
                    copy(
                        isCacheClearing = false,
                        error = e.message ?: "Failed to clear cache",
                    )
                }
            }
        }
    }

    fun revokeAllKeys() {
        stateHandle.scope.launch {
            stateHandle.update { copy(isKeyRevoking = true) }
            when (val result = keyRepository.deleteAllKeys()) {
                is Result.Success -> {
                    stateHandle.update { copy(isKeyRevoking = false) }
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            isKeyRevoking = false,
                            error = result.message ?: "Failed to revoke API keys",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }
}

private fun SharedLink.toDisplayData() = SharedLinkDisplayData(
    shareId = shareId ?: "",
    title = title ?: "Untitled Conversation",
    createdAt = createdAt,
    isPublic = isPublic,
)
