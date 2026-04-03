package com.garfiec.librechat.shared.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.extensions.toInstantOrNull
import com.garfiec.librechat.core.common.extensions.toRelativeDateGroup
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.BannerRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.model.Banner
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EModelEndpoint
import com.garfiec.librechat.core.network.client.TokenManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.extensions.formatMonthAbbrev
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Lightweight snapshot of fields DrawerConversationItem actually renders.
 * Avoids passing the full 28-field Conversation through composition.
 */
@Immutable
data class DrawerConversationDisplayData(
    val conversationId: String,
    val title: String,
    val model: String?,
    val endpoint: EModelEndpoint?,
    val relativeTime: String,
    val isActive: Boolean,
    val isFavorite: Boolean,
)

/**
 * Combined UI state for the drawer sidebar.
 */
@Immutable
data class DrawerUiState(
    val groupedConversations: List<Pair<String, List<DrawerConversationDisplayData>>> = emptyList(),
    val favoriteConversations: List<DrawerConversationDisplayData> = emptyList(),
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
)

/**
 * UI state for a backend version mismatch warning.
 */
@Immutable
data class VersionMismatchState(
    val supportedVersion: String,
    val backendVersion: String,
)

class NavHostViewModel(
    private val authRepository: AuthRepository,
    private val bannerRepository: BannerRepository,
    private val configRepository: ConfigRepository,
    private val conversationRepository: ConversationRepository,
    private val tokenManager: TokenManager,
    private val settingsDataStore: com.garfiec.librechat.core.data.datastore.SettingsDataStore,
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(tokenManager.isAuthenticated)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _versionMismatch = MutableStateFlow<VersionMismatchState?>(null)
    val versionMismatch: StateFlow<VersionMismatchState?> = _versionMismatch.asStateFlow()

    private val _recentConversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val _groupedConversations = MutableStateFlow<List<Pair<String, List<Conversation>>>>(emptyList())
    private val _activeConversationId = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    private val _favoriteConversations = MutableStateFlow<List<Conversation>>(emptyList())
    private var nextCursor: String? = null
    private val _hasMore = MutableStateFlow(true)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)

    private val _banners = MutableStateFlow<List<Banner>>(emptyList())
    val banners: StateFlow<List<Banner>> = _banners.asStateFlow()

    private val _dismissedBannerIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedBannerIds: StateFlow<Set<String>> = _dismissedBannerIds.asStateFlow()

    val sessionExpired: SharedFlow<Unit> = tokenManager.sessionExpiredFlow

    val tabletSidebarOpen: StateFlow<Boolean> = settingsDataStore.tabletSidebarOpen
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            initialValue = false, // Avoid runBlocking — not safe on iOS/Kotlin Native main thread
        )

    val tabletSidebarGestureEnabled: StateFlow<Boolean> = settingsDataStore.tabletSidebarGestureEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _sidebarMode = MutableStateFlow<SidebarMode>(SidebarMode.Conversations)
    val sidebarMode: StateFlow<SidebarMode> = _sidebarMode.asStateFlow()

    private val _selectedSettingsCategory = MutableStateFlow<SettingsCategory?>(null)
    val selectedSettingsCategory: StateFlow<SettingsCategory?> = _selectedSettingsCategory.asStateFlow()

    fun setSidebarMode(mode: SidebarMode) {
        _sidebarMode.value = mode
    }

    fun selectSettingsCategory(category: SettingsCategory) {
        _selectedSettingsCategory.value = category
    }

    private var searchJob: Job? = null

    val drawerUiState: StateFlow<DrawerUiState> = combine(
        combine(
            _groupedConversations,
            _activeConversationId,
            _favorites,
            _favoriteConversations,
            _searchQuery,
        ) { grouped, activeId, favIds, favConvos, query ->
            DrawerDataSnapshot(grouped, activeId, favIds, favConvos, query)
        },
        combine(_isRefreshing, _isLoadingMore, _hasMore) { refreshing, loadingMore, hasMore ->
            Triple(refreshing, loadingMore, hasMore)
        },
    ) { data, (refreshing, loadingMore, hasMore) ->
        DrawerUiState(
            groupedConversations = data.grouped.map { (group, convos) ->
                group to convos.map { it.toDrawerDisplayData(data.activeId, data.favIds) }
            },
            favoriteConversations = data.favConvos.map { it.toDrawerDisplayData(data.activeId, data.favIds) },
            searchQuery = data.query,
            isRefreshing = refreshing,
            isLoadingMore = loadingMore,
            hasMore = hasMore,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DrawerUiState())

    init {
        viewModelScope.launch {
            try {
                val loggedIn = authRepository.isLoggedIn()
                _isLoggedIn.value = loggedIn
                if (loggedIn) {
                    checkBackendVersion()
                }
            } catch (e: Exception) {
                Logger.w(e) { "Failed to check auth state on init" }
            }
        }
        observeConversations()
        observeBookmarks()
        fetchBanners()
        loadInitialConversations()
    }

    private fun observeConversations() {
        viewModelScope.launch {
            try {
                conversationRepository.observeConversations().collect { result ->
                    if (result is Result.Success) {
                        _recentConversations.value = result.data
                        if (_searchQuery.value.isBlank()) {
                            _groupedConversations.value = groupConversationsByDate(result.data)
                        }
                        updateFavoriteConversations()
                    }
                }
            } catch (e: Exception) {
                Logger.w(e) { "Failed to observe conversations" }
            }
        }
    }

    private fun loadInitialConversations() {
        viewModelScope.launch {
            try {
                val result = conversationRepository.loadNextPage(cursor = null)
                if (result is Result.Success) {
                    nextCursor = result.data
                    _hasMore.value = result.data != null
                }
            } catch (e: Exception) {
                Logger.w(e) { "Failed to load initial conversations" }
            }
        }
    }

    fun loadMoreConversations() {
        if (_isLoadingMore.value || !_hasMore.value) return
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val result = conversationRepository.loadNextPage(cursor = nextCursor)
                if (result is Result.Success) {
                    nextCursor = result.data
                    _hasMore.value = result.data != null
                } else {
                    Logger.w { "Failed to load more conversations" }
                }
            } catch (e: Exception) {
                Logger.w(e) { "Failed to load more conversations" }
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun onAuthComplete() {
        _isLoggedIn.value = true
        loadInitialConversations()
        fetchBanners()
        checkBackendVersion()
    }

    fun refreshConversations() {
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                nextCursor = null
                _hasMore.value = true
                val result = conversationRepository.loadNextPage(cursor = null)
                if (result is Result.Success) {
                    nextCursor = result.data
                    _hasMore.value = result.data != null
                }
            } catch (e: Exception) {
                Logger.w(e) { "Failed to refresh conversations" }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun setActiveConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        if (query.isBlank()) {
            _groupedConversations.value = groupConversationsByDate(_recentConversations.value)
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            val filtered = _recentConversations.value.filter { conversation ->
                conversation.title?.contains(query, ignoreCase = true) == true
            }
            _groupedConversations.value = groupConversationsByDate(filtered)
        }
    }

    private fun observeBookmarks() {
        viewModelScope.launch {
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

    fun toggleFavorite(conversationId: String) {
        viewModelScope.launch {
            settingsDataStore.toggleBookmark(conversationId)
        }
    }

    private fun updateFavoriteConversations() {
        val bookmarkedIds = _favorites.value
        val allConversations = _recentConversations.value
        _favoriteConversations.value = allConversations.filter { conversation ->
            conversation.conversationId in bookmarkedIds ||
                conversation.tags.any {
                    it.equals("favorite", ignoreCase = true) ||
                        it.equals("bookmark", ignoreCase = true)
                }
        }
    }

    private fun fetchBanners() {
        viewModelScope.launch {
            try {
                val result = bannerRepository.getBanners()
                if (result is Result.Success) {
                    val now = Clock.System.now()
                    _banners.value = result.data.filter { banner ->
                        val from = banner.displayFrom?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        val to = banner.displayTo?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        val afterStart = from == null || now >= from
                        val beforeEnd = to == null || now < to
                        afterStart && beforeEnd
                    }
                }
            } catch (e: Exception) {
                Logger.w(e) { "Failed to fetch banners" }
            }
        }
    }

    fun setTabletSidebarOpen(open: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setTabletSidebarOpen(open)
        }
    }

    fun dismissBanner(bannerId: String) {
        _dismissedBannerIds.value = _dismissedBannerIds.value + bannerId
    }

    private fun checkBackendVersion() {
        viewModelScope.launch {
            val result = configRepository.checkBackendVersion()
            if (result is Result.Success) {
                val checkResult = result.data
                val detectedVersion = checkResult.backendVersion
                if (!checkResult.isCompatible && detectedVersion != null) {
                    val dismissedVersion = settingsDataStore.dismissedVersionWarning.first()
                    if (dismissedVersion != detectedVersion) {
                        _versionMismatch.value = VersionMismatchState(
                            supportedVersion = checkResult.supportedVersion,
                            backendVersion = detectedVersion,
                        )
                    }
                }
            }
        }
    }

    fun dismissVersionWarning() {
        _versionMismatch.value = null
    }

    fun dismissVersionWarningPermanently() {
        val backendVersion = _versionMismatch.value?.backendVersion
        _versionMismatch.value = null
        if (backendVersion != null) {
            viewModelScope.launch {
                settingsDataStore.setDismissedVersionWarning(backendVersion)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _isLoggedIn.value = false
            _recentConversations.value = emptyList()
            _groupedConversations.value = emptyList()
            _activeConversationId.value = null
            _searchQuery.value = ""
            _favorites.value = emptySet()
            _favoriteConversations.value = emptyList()
            _sidebarMode.value = SidebarMode.Conversations
            _selectedSettingsCategory.value = null
        }
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

private data class DrawerDataSnapshot(
    val grouped: List<Pair<String, List<Conversation>>>,
    val activeId: String?,
    val favIds: Set<String>,
    val favConvos: List<Conversation>,
    val query: String,
)

private fun Conversation.toDrawerDisplayData(
    activeConversationId: String?,
    favoriteIds: Set<String>,
): DrawerConversationDisplayData {
    val convId = conversationId ?: ""
    return DrawerConversationDisplayData(
        conversationId = convId,
        title = title ?: "New Chat",
        model = model,
        endpoint = endpoint,
        relativeTime = updatedAt?.toInstantOrNull()?.toRelativeTimeString() ?: "",
        isActive = convId == activeConversationId,
        isFavorite = convId in favoriteIds,
    )
}

private fun Instant.toRelativeTimeString(): String {
    val now = Clock.System.now()
    val duration = now - this
    val minutes = duration.inWholeMinutes
    val hours = duration.inWholeHours
    val days = duration.inWholeDays

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val date = toLocalDateTime(TimeZone.currentSystemDefault()).date
            "${formatMonthAbbrev(date.monthNumber)} ${date.dayOfMonth}"
        }
    }
}
