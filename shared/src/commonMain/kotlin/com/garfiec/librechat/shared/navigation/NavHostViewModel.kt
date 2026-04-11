package com.garfiec.librechat.shared.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.extensions.firstBlocking
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.BannerRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.model.Banner
import com.garfiec.librechat.core.network.client.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NavHostViewModel(
    private val authRepository: AuthRepository,
    bannerRepository: BannerRepository,
    configRepository: ConfigRepository,
    conversationRepository: ConversationRepository,
    private val tokenManager: TokenManager,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val bannerStateHolder = BannerStateHolder(bannerRepository, viewModelScope)
    private val versionCheckStateHolder = VersionCheckStateHolder(configRepository, settingsDataStore, viewModelScope)
    private val conversationListStateHolder = ConversationListStateHolder(conversationRepository, viewModelScope)
    private val favoritesStateHolder =
        FavoritesStateHolder(settingsDataStore, conversationListStateHolder.recentConversations, viewModelScope)

    private val _isLoggedIn = MutableStateFlow(tokenManager.isAuthenticated)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    val versionMismatch: StateFlow<VersionMismatchState?> = versionCheckStateHolder.versionMismatch

    val banners: StateFlow<List<Banner>> = bannerStateHolder.banners
    val dismissedBannerIds: StateFlow<Set<String>> = bannerStateHolder.dismissedBannerIds

    val sessionExpired: SharedFlow<Unit> = tokenManager.sessionExpiredFlow

    val tabletSidebarOpen: StateFlow<Boolean> = settingsDataStore.tabletSidebarOpen
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            firstBlocking(settingsDataStore.tabletSidebarOpen, false),
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

    val drawerUiState: StateFlow<DrawerUiState> = combine(
        combine(
            conversationListStateHolder.groupedConversations,
            conversationListStateHolder.activeConversationId,
            favoritesStateHolder.favorites,
            favoritesStateHolder.favoriteConversations,
            conversationListStateHolder.searchQuery,
        ) { grouped, activeId, favIds, favConvos, query ->
            DrawerDataSnapshot(grouped, activeId, favIds, favConvos, query)
        },
        combine(
            conversationListStateHolder.isRefreshing,
            conversationListStateHolder.isLoadingMore,
            conversationListStateHolder.hasMore,
        ) { refreshing, loadingMore, hasMore ->
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
                    versionCheckStateHolder.checkBackendVersion()
                }
            } catch (e: Exception) {
                Logger.w(e) { "Failed to check auth state on init" }
            }
        }
        bannerStateHolder.fetchBanners()
    }

    fun loadMoreConversations() {
        conversationListStateHolder.loadMoreConversations()
    }

    fun onAuthComplete() {
        _isLoggedIn.value = true
        conversationListStateHolder.refreshConversations()
        bannerStateHolder.fetchBanners()
        versionCheckStateHolder.checkBackendVersion()
    }

    fun refreshConversations() {
        conversationListStateHolder.refreshConversations()
    }

    fun setActiveConversation(conversationId: String?) {
        conversationListStateHolder.setActiveConversation(conversationId)
    }

    fun onSearchQueryChanged(query: String) {
        conversationListStateHolder.onSearchQueryChanged(query)
    }

    fun toggleFavorite(conversationId: String) {
        favoritesStateHolder.toggleFavorite(conversationId)
    }

    fun setTabletSidebarOpen(open: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setTabletSidebarOpen(open)
        }
    }

    fun dismissBanner(bannerId: String) {
        bannerStateHolder.dismissBanner(bannerId)
    }

    fun dismissVersionWarning() {
        versionCheckStateHolder.dismissVersionWarning()
    }

    fun dismissVersionWarningPermanently() {
        versionCheckStateHolder.dismissVersionWarningPermanently()
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _isLoggedIn.value = false
            conversationListStateHolder.reset()
            favoritesStateHolder.reset()
            _sidebarMode.value = SidebarMode.Conversations
            _selectedSettingsCategory.value = null
        }
    }
}
