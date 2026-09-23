package com.garfiec.librechat.feature.conversations.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import com.garfiec.librechat.core.common.identity.AccountTransition
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.accountTransitions
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.engine.RecentMission
import com.garfiec.librechat.core.data.engine.RecentMissionsSource
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.canCreateSharedLinks
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import com.garfiec.librechat.feature.conversations.export.ConversationExporter
import com.garfiec.librechat.feature.conversations.export.ExportFormat
import com.garfiec.librechat.feature.conversations.viewmodel.ConversationListActionsDelegate
import com.garfiec.librechat.feature.conversations.viewmodel.ConversationListEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Drawer-data half of the navigation shell, split out of `NavHostViewModel` so `:shared` stays nav
 * glue rather than a stealth feature module. Owns the drawer's conversation list, favorites/pinned sections,
 * long-press action menu, Projects tab, and the drawer's "Library" tab preference. Activity-scoped
 * (registered as a plain `viewModelOf`), so a single instance is shared by the phone drawer, the
 * tablet sidebar, and the nav host's active-conversation tracking.
 */
class DrawerViewModel(
    private val conversationRepository: ConversationRepository,
    private val roleRepository: RoleRepository,
    private val tagRepository: TagRepository,
    private val projectRepository: ProjectRepository,
    private val configRepository: ConfigRepository,
    private val shareRepository: ShareRepository,
    private val conversationExporter: ConversationExporter,
    private val activeAccountProvider: ActiveAccountProvider,
    /**
     * The engine's missions, listed among the chats. Null wherever the engine's graph is not
     * started — iOS today (D-034) — and the recents list is then the chats alone.
     */
    private val recentMissionsSource: RecentMissionsSource? = null,
) : ViewModel() {

    private val conversationListStateHolder = ConversationListStateHolder(conversationRepository, viewModelScope)

    private val missions = MutableStateFlow<List<RecentMission>>(emptyList())
    private var missionsFetchedAt: TimeMark? = null
    private var missionsJob: Job? = null

    private val favoriteConversations: StateFlow<List<Conversation>> =
        conversationListStateHolder.recentConversations
            .map { list -> list.filter { SAVED_TAG in it.tags } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val pinnedConversations: StateFlow<List<Conversation>> =
        conversationListStateHolder.recentConversations
            .map { list -> list.filter { it.pinned == true } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Inputs for the drawer long-press action menu: the user-defined tags (excluding favorites,
    // same filter as ConversationListViewModel.observeTags) for the tag picker, plus the
    // config-driven shared-links flag and the SHARED_LINKS role permission that gate the Share action.
    private val drawerActionMenuState: StateFlow<DrawerActionMenuState> =
        combine(
            tagRepository.observeTags()
                .map { tags -> tags.filter { it.count > 0 && it.tag != SAVED_TAG } },
            configRepository.startupConfig,
            configRepository.detectedBackendVersion,
            roleRepository.userPermissions,
        ) { tags, config, version, permissions ->
            // Gate fail-closed on unknown version so older servers don't surface actions
            // they'd 404 on. Thresholds differ: POST /api/convos/pin landed BETWEEN
            // v0.8.7-rc1 and v0.8.7 final (upstream 743f57f63), so pin needs the final;
            // /api/projects was already in v0.8.7-rc1.
            val pinSupported = version != null && BackendVersion.isCompatibleOrNewer(version, "0.8.7")
            val projectsSupported = version != null && BackendVersion.isCompatibleOrNewer(version, "0.8.7-rc1")
            val canShare = permissions.canCreateSharedLinks(config?.sharedLinksEnabled ?: false)
            DrawerActionMenuState(tags, canShare, pinSupported, projectsSupported)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DrawerActionMenuState())

    // Chat Projects (v0.8.7). Loaded lazily for the move-to-project picker and the drawer's Projects
    // tab folder list; the server is the source of truth (no local cache).
    private val _projects = MutableStateFlow<List<ChatProject>>(emptyList())
    val projects: StateFlow<List<ChatProject>> = _projects.asStateFlow()

    // One-shot events for the drawer action menu (share-link copied, export-ready,
    // navigate-to-duplicate, errors). Reuses the conversation-list event type. extraBufferCapacity
    // lets emit() return immediately instead of suspending if the collector (ConversationActionEffects)
    // is momentarily absent — e.g. an action's result arriving as the drawer leaves composition.
    private val _events = MutableSharedFlow<ConversationListEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ConversationListEvent> = _events.asSharedFlow()

    // Shared row-action logic (rename/archive/delete/share/duplicate/export) — the same delegate
    // ConversationListViewModel and ProjectChatsViewModel use, so the drawer long-press menu surfaces
    // Result.Error failures (toast) instead of swallowing them in a dead try/catch around a
    // Result-returning repo call. Room-backed here, so onMutated is a no-op: the conversation
    // observe-Flow already re-emits after a mutation.
    private val conversationActions = ConversationListActionsDelegate(
        scope = viewModelScope,
        events = _events,
        conversationRepository = conversationRepository,
        tagRepository = tagRepository,
        shareRepository = shareRepository,
        conversationExporter = conversationExporter,
        onMutated = {},
    )

    /**
     * Role-permission flags for drawer UI. Permissive-default (`?: true`) until the
     * role loads; once it does, denied permissions flip the flags off and the drawer
     * re-composes to hide the corresponding surfaces.
     */
    private val drawerPermissionFlags: StateFlow<DrawerPermissionFlags> =
        roleRepository.userPermissions
            .map { role ->
                DrawerPermissionFlags(
                    agentsEnabled = role.hasAccessOrPermissive(PermissionType.AGENTS, Permission.USE),
                    bookmarksEnabled = role.hasAccessOrPermissive(PermissionType.BOOKMARKS, Permission.USE),
                    // USE gate is fail-OPEN (read/list visibility); mutation gating
                    // lives fail-CLOSED inside feature/skills.
                    skillsEnabled = role.hasAccessOrPermissive(PermissionType.SKILLS, Permission.USE),
                )
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, DrawerPermissionFlags())

    /**
     * All three conversation sections, mapped to row data.
     *
     * Kept deliberately upstream of `searchQuery`: that flow changes on every keystroke, and folding
     * it into this combine would re-map all three lists per character — including the grouped list,
     * which ConversationListStateHolder debounces at 300ms and which is therefore usually identical
     * to the previous keystroke's. Splitting it out means a keystroke only re-assembles
     * [DrawerUiState] from rows that were already mapped.
     */
    private val displayConversations: Flow<DrawerDisplaySnapshot> = combine(
        conversationListStateHolder.groupedConversations,
        favoriteConversations,
        pinnedConversations,
        conversationListStateHolder.activeConversationId,
        configRepository.endpointConfigs,
    ) { grouped, favConvos, pinnedConvos, activeId, endpointConfigs ->
        DrawerDisplaySnapshot(
            grouped = grouped.map { (group, convos) ->
                group to convos.map { it.toDrawerDisplayData(activeId, endpointConfigs) }
            },
            favorites = favConvos.map { it.toDrawerDisplayData(activeId, endpointConfigs) },
            pinned = pinnedConvos.map { it.toDrawerDisplayData(activeId, endpointConfigs) },
        )
    }

    val drawerUiState: StateFlow<DrawerUiState> = combine(
        combine(displayConversations, missions) { display, recent -> display to recent },
        conversationListStateHolder.searchQuery,
        combine(
            conversationListStateHolder.isRefreshing,
            conversationListStateHolder.isLoadingMore,
            conversationListStateHolder.hasMore,
        ) { refreshing, loadingMore, hasMore ->
            Triple(refreshing, loadingMore, hasMore)
        },
        drawerPermissionFlags,
        drawerActionMenuState,
    ) { (display, recentMissions), query, refreshState, perms, actionMenu ->
        val (refreshing, loadingMore, hasMore) = refreshState
        DrawerUiState(
            groupedConversations = display.grouped,
            groupedRecents = mergeRecents(
                grouped = display.grouped,
                missions = recentMissions.toDrawerMissions(query),
                reference = RelativeTimeReference.current(),
            ),
            favoriteConversations = display.favorites,
            pinnedConversations = display.pinned,
            searchQuery = query,
            isRefreshing = refreshing,
            isLoadingMore = loadingMore,
            hasMore = hasMore,
            agentsEnabled = perms.agentsEnabled,
            bookmarksEnabled = perms.bookmarksEnabled,
            skillsEnabled = perms.skillsEnabled,
            availableTags = actionMenu.availableTags,
            sharedLinksEnabled = actionMenu.sharedLinksEnabled,
            pinEnabled = actionMenu.pinEnabled,
            projectsEnabled = actionMenu.projectsEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DrawerUiState())

    private data class DrawerPermissionFlags(
        val agentsEnabled: Boolean = true,
        val bookmarksEnabled: Boolean = true,
        val skillsEnabled: Boolean = true,
    )

    private data class DrawerActionMenuState(
        val availableTags: List<ConversationTag> = emptyList(),
        val sharedLinksEnabled: Boolean = false,
        val pinEnabled: Boolean = false,
        val projectsEnabled: Boolean = false,
    )

    init {
        refreshMissions()
        // Load the Chat Projects folders once the backend is known to support them (v0.8.7-rc1+).
        // detectedBackendVersion is a StateFlow (already conflated), so no distinctUntilChanged.
        viewModelScope.launch {
            configRepository.detectedBackendVersion.collect { version ->
                if (version != null && BackendVersion.isCompatibleOrNewer(version, "0.8.7-rc1")) {
                    loadProjects()
                }
            }
        }
        // The active account changed underneath this Activity-scoped VM. This is the drawer half of
        // the reset the nav shell also performs (NavHostViewModel runs the nav/session half against
        // its own independent accountTransitions() subscription — the flow is cold per-collector, so
        // two subscribers are safe). Everything held in-memory here or in the singleton repos below
        // is account- or server-blind and must not survive the flip; Room-backed state re-filters
        // reactively via the active-account gate.
        viewModelScope.launch {
            activeAccountProvider.accountTransitions().collect { transition ->
                _projects.value = emptyList()
                conversationListStateHolder.reset()
                // No tagRepository.clearCache() here: observeTags() is account-scoped, so the
                // outgoing account's tags never show, and RefreshTagsSessionTask (fired by the nav
                // shell's runAll on Switched) does an atomic replaceAllForAccount. A clearCache() in
                // this separate collector would race that write with no happens-before and could wipe
                // the freshly-fetched tags (they ran sequentially in one collector before the split).
                if (transition is AccountTransition.Switched) {
                    // The switch path never runs the login-side session machinery, so the incoming
                    // account's conversation list is fetched here. loadProjects() is NOT called here:
                    // it rides the version-gated collector above once the nav shell's re-detect
                    // re-emits detectedBackendVersion. Not refreshed on Ended (logout) — that would
                    // fire network calls with no active session.
                    conversationListStateHolder.refreshConversations()
                }
            }
        }
    }

    fun loadMoreConversations() {
        conversationListStateHolder.loadMoreConversations()
    }

    /**
     * Pull-to-refresh: refreshes the drawer conversation list + favorites + tags. The explicit tag
     * refresh is wanted here because a manual refresh has no session-task machinery behind it.
     */
    fun refreshConversations() {
        conversationListStateHolder.refreshConversations()
        viewModelScope.launch { tagRepository.refreshTags() }
        refreshMissions(force = true)
    }

    /**
     * Re-reads the engine's missions. Called when the drawer opens, so what it shows is at most one
     * opening old — a mission's state moves while nobody looks, which a chat list mostly does not.
     *
     * Throttled unless [force]d: opening and closing the drawer three times in ten seconds is a
     * gesture, not three requests for news. Three round trips each time (the source's contract),
     * never a transcript.
     */
    fun refreshMissions(force: Boolean = false) {
        val source = recentMissionsSource ?: return
        if (missionsJob?.isActive == true) return
        val fetchedAt = missionsFetchedAt
        if (!force && fetchedAt != null && fetchedAt.elapsedNow() < MISSIONS_MIN_INTERVAL) return
        missionsJob = viewModelScope.launch {
            missions.value = source.recentMissions()
            missionsFetchedAt = TimeSource.Monotonic.markNow()
        }
    }

    /**
     * Fresh-login refresh (conversations + favorites only). accountTransitions() does not fire on
     * login-from-logged-out, so the nav host's onAuthComplete drives this. Tags/roles/favorites
     * session tasks already fired from AuthRepositoryImpl on the login success, so we must NOT
     * refresh tags here — that was the source of the double-fetch on fresh login.
     */
    fun refreshConversationsAfterLogin() {
        conversationListStateHolder.refreshConversations()
    }

    fun setActiveConversation(conversationId: String?) {
        conversationListStateHolder.setActiveConversation(conversationId)
    }

    fun onSearchQueryChanged(query: String) {
        conversationListStateHolder.onSearchQueryChanged(query)
    }

    fun toggleFavorite(conversationId: String, currentTags: List<String>) {
        viewModelScope.launch {
            val result = tagRepository.toggleFavorite(conversationId, currentTags)
            if (result is Result.Error) {
                Logger.w(result.exception) { "Failed to toggle favorite for $conversationId" }
                // Surface the failure like the row-action delegate does, instead of only logging —
                // otherwise a failed favorite from the drawer gives the user no feedback.
                _events.emit(ConversationListEvent.ShowError(result.message ?: "Failed to update favorite"))
            }
        }
    }

    // --- Drawer conversation action menu (long-press). Row actions route through the shared
    // ConversationListActionsDelegate; favorite/pin/tags stay local (different signatures). ---

    fun renameConversation(id: String, newTitle: String) = conversationActions.renameConversation(id, newTitle)

    fun archiveConversation(id: String) = conversationActions.archiveConversation(id)

    fun pinConversation(id: String, pinned: Boolean) {
        viewModelScope.launch {
            val result = conversationRepository.pin(id, pinned)
            if (result is Result.Error) {
                Logger.e(result.exception) { "Failed to pin conversation" }
                _events.emit(ConversationListEvent.ShowError("Failed to pin conversation"))
            }
        }
    }

    /** Loads the user's projects for the move-to-project picker. */
    fun loadProjects() {
        viewModelScope.launch {
            when (val result = projectRepository.listProjects()) {
                is Result.Success -> _projects.value = result.data.projects
                is Result.Error -> Logger.w(result.exception) { "Failed to load projects" }
                is Result.Loading -> Unit
            }
        }
    }

    /** Assigns [conversationId] to [projectId], or unassigns it when null. */
    fun moveConversationToProject(conversationId: String, projectId: String?) {
        viewModelScope.launch {
            when (val result = projectRepository.assignConversation(conversationId, projectId)) {
                is Result.Success -> onProjectAssignmentChanged()
                is Result.Error -> {
                    Logger.e(result.exception) { "Failed to move conversation to project" }
                    _events.emit(ConversationListEvent.ShowError("Failed to move conversation"))
                }
                is Result.Loading -> Unit
            }
        }
    }

    /** Creates a project named [name] and assigns [conversationId] to it. */
    fun createProjectAndAssign(conversationId: String, name: String) {
        viewModelScope.launch {
            when (val created = projectRepository.createProject(name)) {
                is Result.Success -> {
                    when (val assign = projectRepository.assignConversation(conversationId, created.data.id)) {
                        is Result.Error -> {
                            Logger.e(assign.exception) { "Failed to assign new project" }
                            _events.emit(ConversationListEvent.ShowError("Failed to move conversation"))
                        }
                        is Result.Success -> onProjectAssignmentChanged()
                        is Result.Loading -> Unit
                    }
                }
                is Result.Error -> {
                    Logger.e(created.exception) { "Failed to create project" }
                    _events.emit(ConversationListEvent.ShowError("Failed to create project"))
                }
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Refresh the move-to-project picker after a conversation's project assignment changes: reload the
     * folders, whose conversationCount is now stale.
     */
    private fun onProjectAssignmentChanged() {
        loadProjects()
    }

    fun deleteConversation(id: String) = conversationActions.deleteConversation(
        id = id,
        // See the delegate KDoc: isActive drives the navigate-off when the open chat is deleted.
        isActive = conversationListStateHolder.activeConversationId.value == id,
    )

    fun shareConversation(conversationId: String) = conversationActions.shareConversation(conversationId)

    fun duplicateConversation(conversationId: String, title: String?) =
        conversationActions.duplicateConversation(conversationId, title)

    fun exportConversation(conversationId: String, title: String?, format: ExportFormat) =
        conversationActions.exportConversation(conversationId, title, format)

    /**
     * Persists the user-chosen tags for [conversationId], preserving favorite status.
     * [currentTags] is the conversation's existing tag list (to detect the SAVED_TAG).
     */
    fun updateConversationTags(conversationId: String, currentTags: List<String>, userTags: List<String>) {
        val wasFavorited = SAVED_TAG in currentTags
        val cleaned = userTags.filterNot { it == SAVED_TAG }
        val finalTags = if (wasFavorited) cleaned + SAVED_TAG else cleaned
        viewModelScope.launch {
            when (tagRepository.setConversationTags(conversationId, finalTags)) {
                is Result.Success -> {
                    tagRepository.refreshTags()
                }
                is Result.Error -> {
                    _events.emit(ConversationListEvent.ShowError("Failed to update tags"))
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }
}

/** See [DrawerViewModel.refreshMissions]. */
private val MISSIONS_MIN_INTERVAL = 15.seconds
