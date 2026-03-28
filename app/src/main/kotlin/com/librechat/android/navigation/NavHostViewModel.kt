package com.librechat.android.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.librechat.android.core.data.repository.AuthRepository
import com.librechat.android.core.data.repository.BannerRepository
import com.librechat.android.core.data.repository.ConfigRepository
import com.librechat.android.core.data.repository.ConversationRepository
import com.librechat.android.core.data.repository.VersionCheckResult
import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.extensions.toInstantOrNull
import com.librechat.android.core.common.extensions.toRelativeDateGroup
import com.librechat.android.core.model.Banner
import com.librechat.android.core.model.Conversation
import com.librechat.android.core.model.EModelEndpoint
import com.librechat.android.core.network.client.TokenManager
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Combined UI state for the drawer sidebar. A single emission replaces
 * 10+ individual StateFlow collections in the parent composable, preventing
 * unnecessary recomposition of the entire PhoneLayout/TabletLayout tree.
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
    private val settingsDataStore: com.librechat.android.core.data.datastore.SettingsDataStore,
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    /**
     * Non-null when a version mismatch has been detected and should be shown to the user.
     * Set to null after the user dismisses the dialog (for this session or permanently).
     */
    private val _versionMismatch = MutableStateFlow<VersionMismatchState?>(null)
    val versionMismatch: StateFlow<VersionMismatchState?> = _versionMismatch.asStateFlow()

    private val _recentConversations = MutableStateFlow<List<Conversation>>(emptyList())

    /** Conversations grouped by date (internal, feeds into drawerUiState). */
    private val _groupedConversations = MutableStateFlow<List<Pair<String, List<Conversation>>>>(emptyList())

    /** The ID of the currently viewed conversation (for active highlight). */
    private val _activeConversationId = MutableStateFlow<String?>(null)

    /** Drawer search query. */
    private val _searchQuery = MutableStateFlow("")

    /** Server-synced bookmarked conversation IDs. */
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())

    /** Bookmarked/favorite conversations (internal, feeds into drawerUiState). */
    private val _favoriteConversations = MutableStateFlow<List<Conversation>>(emptyList())

    /** Pagination cursor for loading more conversations from the server. */
    private var nextCursor: String? = null

    /** Whether there are more conversations to load from the server. */
    private val _hasMore = MutableStateFlow(true)

    /** Whether a load-more request is in progress. */
    private val _isLoadingMore = MutableStateFlow(false)

    /** Whether a pull-to-refresh is in progress. */
    private val _isRefreshing = MutableStateFlow(false)

    /** Active banners from the server, filtered to non-expired. */
    private val _banners = MutableStateFlow<List<Banner>>(emptyList())
    val banners: StateFlow<List<Banner>> = _banners.asStateFlow()

    /** Banner IDs dismissed by the user during this session. */
    private val _dismissedBannerIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedBannerIds: StateFlow<Set<String>> = _dismissedBannerIds.asStateFlow()

    val sessionExpired: SharedFlow<Unit> = tokenManager.sessionExpiredFlow

    /** Persisted sidebar open/close state for tablet mode.
     *  The initial value is read synchronously so that first composition
     *  sees the real persisted state instead of a hardcoded default,
     *  avoiding a visible open-then-close flicker on cold start. */
    val tabletSidebarOpen: StateFlow<Boolean> = settingsDataStore.tabletSidebarOpen
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            runBlocking { settingsDataStore.tabletSidebarOpen.first() },
        )

    /** Whether the swipe gesture to open/close the tablet sidebar is enabled. */
    val tabletSidebarGestureEnabled: StateFlow<Boolean> = settingsDataStore.tabletSidebarGestureEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Current sidebar mode: Conversations (default) or Settings. */
    private val _sidebarMode = MutableStateFlow<SidebarMode>(SidebarMode.Conversations)
    val sidebarMode: StateFlow<SidebarMode> = _sidebarMode.asStateFlow()

    /** Currently selected settings category in the sidebar nav menu. */
    private val _selectedSettingsCategory = MutableStateFlow<SettingsCategory?>(null)
    val selectedSettingsCategory: StateFlow<SettingsCategory?> = _selectedSettingsCategory.asStateFlow()

    fun setSidebarMode(mode: SidebarMode) {
        _sidebarMode.value = mode
    }

    fun selectSettingsCategory(category: SettingsCategory) {
        _selectedSettingsCategory.value = category
    }

    private var searchJob: Job? = null

    /**
     * Single combined drawer UI state. All display-data mapping (Conversation →
     * DrawerConversationDisplayData) happens here in the ViewModel, not in composition.
     * Collected inside DrawerContent so state changes only recompose the drawer,
     * not the entire PhoneLayout/TabletLayout.
     */
    val drawerUiState: StateFlow<DrawerUiState> = combine(
        combine(_groupedConversations, _activeConversationId, _favorites, _favoriteConversations, _searchQuery) { grouped, activeId, favIds, favConvos, query ->
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
            val loggedIn = authRepository.isLoggedIn()
            _isLoggedIn.value = loggedIn
            // Check backend version on cold start for returning users
            if (loggedIn) {
                checkBackendVersion()
            }
        }
        observeConversations()
        observeBookmarks()
        fetchBanners()
        loadInitialConversations()
    }

    private fun observeConversations() {
        viewModelScope.launch {
            conversationRepository.observeConversations().collect { result ->
                if (result is Result.Success) {
                    _recentConversations.value = result.data
                    // Only regroup if not in search mode
                    if (_searchQuery.value.isBlank()) {
                        _groupedConversations.value = groupConversationsByDate(result.data)
                    }
                    updateFavoriteConversations()
                }
            }
        }
    }

    private fun loadInitialConversations() {
        viewModelScope.launch {
            val result = conversationRepository.loadNextPage(cursor = null)
            if (result is Result.Success) {
                nextCursor = result.data
                _hasMore.value = result.data != null
            }
        }
    }

    fun loadMoreConversations() {
        if (_isLoadingMore.value || !_hasMore.value) return
        _isLoadingMore.value = true
        viewModelScope.launch {
            val result = conversationRepository.loadNextPage(cursor = nextCursor)
            if (result is Result.Success) {
                nextCursor = result.data
                _hasMore.value = result.data != null
            } else {
                Timber.w("Failed to load more conversations")
            }
            _isLoadingMore.value = false
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
            nextCursor = null
            _hasMore.value = true
            val result = conversationRepository.loadNextPage(cursor = null)
            if (result is Result.Success) {
                nextCursor = result.data
                _hasMore.value = result.data != null
            }
            _isRefreshing.value = false
        }
    }

    fun setActiveConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        if (query.isBlank()) {
            // Reset to full grouped list from cached conversations
            _groupedConversations.value = groupConversationsByDate(_recentConversations.value)
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // debounce
            val filtered = _recentConversations.value.filter { conversation ->
                conversation.title?.contains(query, ignoreCase = true) == true
            }
            _groupedConversations.value = groupConversationsByDate(filtered)
        }
    }

    private fun observeBookmarks() {
        viewModelScope.launch {
            settingsDataStore.bookmarkedConversationIds.collect { bookmarkedIds ->
                _favorites.value = bookmarkedIds
                updateFavoriteConversations()
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
            val result = bannerRepository.getBanners()
            if (result is Result.Success) {
                val now = Instant.now()
                _banners.value = result.data.filter { banner ->
                    val from = banner.displayFrom?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    val to = banner.displayTo?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    val afterStart = from == null || !now.isBefore(from)
                    val beforeEnd = to == null || now.isBefore(to)
                    afterStart && beforeEnd
                }
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

    /**
     * Performs an async, non-blocking version check against the backend.
     * If the versions are incompatible and the user hasn't dismissed the warning
     * for this specific backend version, sets [versionMismatch] to trigger the dialog.
     */
    private fun checkBackendVersion() {
        viewModelScope.launch {
            val result = configRepository.checkBackendVersion()
            if (result is Result.Success) {
                val checkResult = result.data
                val detectedVersion = checkResult.backendVersion
                if (!checkResult.isCompatible && detectedVersion != null) {
                    // Check if the user has already dismissed the warning for this version
                    val dismissedVersion = settingsDataStore.dismissedVersionWarning.first()
                    if (dismissedVersion != detectedVersion) {
                        _versionMismatch.value = VersionMismatchState(
                            supportedVersion = checkResult.supportedVersion,
                            backendVersion = detectedVersion,
                        )
                    } else {
                        Timber.d(
                            "Version mismatch (backend=%s, supported=%s) but user dismissed for this version",
                            detectedVersion,
                            checkResult.supportedVersion,
                        )
                    }
                }
            }
            // On error, silently ignore -- the version check is non-critical
        }
    }

    /** Dismiss the version mismatch dialog for this session only. */
    fun dismissVersionWarning() {
        _versionMismatch.value = null
    }

    /** Dismiss the version mismatch dialog and suppress it for this backend version permanently. */
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

/** Intermediate holder for the 5-argument combine. */
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
    val now = Instant.now()
    val minutes = ChronoUnit.MINUTES.between(this, now)
    val hours = ChronoUnit.HOURS.between(this, now)
    val days = ChronoUnit.DAYS.between(this, now)

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d"))
    }
}
