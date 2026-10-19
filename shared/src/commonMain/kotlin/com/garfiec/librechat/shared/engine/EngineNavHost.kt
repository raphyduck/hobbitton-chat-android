package com.garfiec.librechat.shared.engine

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.data.engine.OpenConversation
import com.garfiec.librechat.core.ui.theme.AppLocale
import com.garfiec.librechat.core.ui.util.SafeUriHandler
import com.garfiec.librechat.feature.auth.screen.PortalSignInScreen
import com.garfiec.librechat.feature.tasks.navigation.EngineChat
import com.garfiec.librechat.feature.tasks.navigation.engineChatEntries
import com.garfiec.librechat.feature.tasks.navigation.tasksEntries
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app's root since D-077: one engine, two profiles.
 *
 * Signed out, the portal's sign-in ([PortalSignInScreen]); signed in, the chat — an engine session
 * on the `chat` profile — with a drawer of recent conversations (chats and tasks), the Tasks tab
 * and the settings.
 */
@Composable
fun EngineNavHost(
    modifier: Modifier = Modifier,
    appLocaleTag: String? = null,
    /**
     * Asks the platform for the right to post notifications (Android 13+), when the Settings switch
     * is turned on. The activity owns the request; without one the switch still saves.
     */
    onRequestNotificationPermission: () -> Unit = {},
    viewModel: EngineShellViewModel = koinViewModel(),
) {
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val allChats by viewModel.allChats.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val addresses by viewModel.addresses.collectAsStateWithLifecycle()
    val attentionSound by viewModel.attentionSound.collectAsStateWithLifecycle()
    val localDictation by viewModel.localDictation.collectAsStateWithLifecycle()
    val chatFontSize by viewModel.chatFontSize.collectAsStateWithLifecycle()
    val defaultChatModel by viewModel.defaultChatModel.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val openRequest by viewModel.openRequest.collectAsStateWithLifecycle()
    val identity by viewModel.identity.collectAsStateWithLifecycle()

    // Back in the foreground: a renewal refused while away has emptied the token store, and the
    // shell must go back to the sign-in rather than fail every request of the chat.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.recheck() }

    // The one link gate (review C5): message links and mission notes open http(s) and mailto only.
    val platformUriHandler = LocalUriHandler.current
    val safeUriHandler = remember(platformUriHandler) { SafeUriHandler(platformUriHandler) }

    CompositionLocalProvider(LocalUriHandler provides safeUriHandler) {
        AppLocale(tag = appLocaleTag) {
            Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                when (signedIn) {
                    // Still reading the store: the background only, never a flash of the wrong screen.
                    null -> Unit
                    false -> PortalSignInScreen(onSignedIn = viewModel::onSignedIn)
                    true -> {
                        val account = DrawerAccount(
                            name = identity?.nom?.takeIf { it.isNotBlank() } ?: identity?.prenom?.takeIf { it.isNotBlank() },
                            host = addresses?.baseUrl?.let(::hostOf),
                        )
                        EngineMainLayout(
                            chats = chats,
                            allChats = allChats,
                            account = account,
                            settings = EngineSettingsUiState(
                                themeMode = themeMode,
                                addresses = addresses,
                                attentionSound = attentionSound,
                                localDictation = localDictation,
                                chatFontSize = chatFontSize,
                                account = account,
                                defaultModelId = defaultChatModel,
                                language = AppLanguage.fromTag(selectedLanguage),
                                models = models.models,
                                engineDefault = models.engineDefault,
                            ),
                            openRequest = openRequest,
                            onConsumeOpenRequest = viewModel::consumeOpenRequest,
                            onRefreshChats = viewModel::refreshChats,
                            onRefreshAllChats = viewModel::refreshAllChats,
                            onMarkRead = viewModel::markRead,
                            onThemeMode = viewModel::setThemeMode,
                            onChatFontSize = viewModel::setChatFontSize,
                            onAttentionSound = { enabled ->
                                viewModel.setAttentionSound(enabled)
                                if (enabled) onRequestNotificationPermission()
                            },
                            onLocalDictation = viewModel::setLocalDictation,
                            onDefaultModel = viewModel::setDefaultChatModel,
                            onLanguage = { viewModel.setSelectedLanguage(it.tag) },
                            onLoadModels = viewModel::loadModels,
                            onSignOut = viewModel::signOut,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The signed-in layout: a modal drawer over one back stack, rooted on the chat. The back stack is
 * created here, inside the signed-in branch, so a sign-out drops it and the next sign-in starts on
 * a blank chat rather than on the previous person's conversation.
 */
@Composable
private fun EngineMainLayout(
    chats: EngineChatsState,
    allChats: EngineChatsState,
    account: DrawerAccount,
    settings: EngineSettingsUiState,
    openRequest: OpenConversation?,
    onConsumeOpenRequest: (OpenConversation) -> Unit,
    onRefreshChats: () -> Unit,
    onRefreshAllChats: () -> Unit,
    onMarkRead: (String) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onChatFontSize: (ChatFontSize) -> Unit,
    onAttentionSound: (Boolean) -> Unit,
    onLocalDictation: (Boolean) -> Unit,
    onDefaultModel: (String?) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onLoadModels: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(engineShellSavedStateConfig, EngineChat())
    val navigator = remember(backStack) { EngineNavigator(backStack) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val refreshChats by rememberUpdatedState(onRefreshChats)
    val refreshAllChats by rememberUpdatedState(onRefreshAllChats)
    val loadModels by rememberUpdatedState(onLoadModels)

    // The drawer re-reads the chats each time it opens: a chat started on another device, or
    // answered while the drawer was shut, moves to the top.
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) refreshChats()
    }

    // A tapped notification: its conversation as the root, as the drawer would open it.
    val consumeOpenRequest by rememberUpdatedState(onConsumeOpenRequest)
    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        navigator.openChat(request.sessionId, request.title.orEmpty())
        drawerState.close()
        consumeOpenRequest(request)
    }

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
    val activeChat = (navigator.currentRoute as? EngineChat)?.sessionId

    // What is on screen is read (16/10/2026): the conversation as it opens, and again as it is left,
    // so a reply that arrived while it was shown does not come back unread. Leaving the app counts
    // as leaving it, the same as opening another one.
    val markRead by rememberUpdatedState(onMarkRead)
    LaunchedEffect(activeChat) {
        val shown = activeChat ?: return@LaunchedEffect
        markRead(shown)
        try {
            awaitCancellation()
        } finally {
            markRead(shown)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { activeChat?.let(markRead) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet(drawerState = drawerState) {
                EngineDrawerContent(
                    state = chats,
                    activeSessionId = activeChat,
                    onNewChat = {
                        closeDrawer()
                        navigator.newChat()
                    },
                    onOpenChat = { chat ->
                        closeDrawer()
                        navigator.openChat(chat.sessionId, chat.title)
                    },
                    onOpenConversations = {
                        closeDrawer()
                        navigator.openConversations()
                    },
                    onOpenTasks = {
                        closeDrawer()
                        navigator.openTasks()
                    },
                    onOpenSettings = {
                        closeDrawer()
                        navigator.openSettings()
                    },
                    onRetry = onRefreshChats,
                    account = account,
                )
            }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.goBack() },
            modifier = Modifier.fillMaxSize(),
            // Pushed screens (Programmées, the settings) slide in from the edge over a short fade
            // and slide back out; a predictive back shrinks the leaving screen under the finger.
            // Between two chats the entry's own metadata asks for a cross-fade instead.
            transitionSpec = {
                (slideInHorizontally(tween(PUSH_MS)) { it / PUSH_FRACTION } + fadeIn(tween(PUSH_MS)))
                    .togetherWith(fadeOut(tween(PUSH_MS)))
            },
            popTransitionSpec = {
                fadeIn(tween(PUSH_MS))
                    .togetherWith(slideOutHorizontally(tween(PUSH_MS)) { it / PUSH_FRACTION } + fadeOut(tween(PUSH_MS)))
            },
            predictivePopTransitionSpec = { _ ->
                fadeIn(tween(PUSH_MS))
                    .togetherWith(scaleOut(tween(PUSH_MS), targetScale = PREDICTIVE_SCALE) + fadeOut(tween(PUSH_MS)))
            },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                engineChatEntries(
                    onOpenDrawer = openDrawer,
                    onChatStart = { sessionId, title ->
                        navigator.chatStarted(sessionId, title)
                        refreshChats()
                    },
                    onNewChat = { navigator.newChat() },
                    onBack = { navigator.goBack() },
                )
                tasksEntries(
                    onBack = { navigator.goBack() },
                    onOpenDrawer = openDrawer,
                )
                entry<EngineAppSettings> {
                    // Read on every arrival: the deployment's models may have moved since.
                    LaunchedEffect(Unit) { loadModels() }
                    EngineSettingsScreen(
                        state = settings,
                        onBack = { navigator.goBack() },
                        onOpenInstructions = { navigator.openInstructions() },
                        onOpenUsage = { navigator.openUsage() },
                        onOpenBudget = { navigator.openBudget() },
                        onThemeMode = onThemeMode,
                        onChatFontSize = onChatFontSize,
                        onAttentionSound = onAttentionSound,
                        onLocalDictation = onLocalDictation,
                        onDefaultModel = onDefaultModel,
                        onLanguage = onLanguage,
                        onSignOut = {
                            // The stack first: its conversations close with their entries — their
                            // view models, their live feeds — before the portal's tokens go.
                            navigator.newChat()
                            onSignOut()
                        },
                    )
                }
                entry<EngineConversations> {
                    // Read on every arrival: a run that finished while the page was shut moves up.
                    LaunchedEffect(Unit) { refreshAllChats() }
                    EngineConversationsScreen(
                        state = allChats,
                        onBack = { navigator.goBack() },
                        onOpenChat = { chat -> navigator.openChat(chat.sessionId, chat.title) },
                        onRefresh = onRefreshAllChats,
                    )
                }
                entry<EngineInstructions> {
                    EngineInstructionsScreen(onClose = { navigator.goBack() })
                }
                entry<EngineBudget> {
                    EngineBudgetScreen(onClose = { navigator.goBack() })
                }
            },
        )
    }
}

/** « agent.hobbitton.at » out of « https://agent.hobbitton.at/ »: what the drawer's foot shows of the platform. */
private fun hostOf(baseUrl: String): String? = baseUrl
    .trim()
    .substringAfter("://", baseUrl.trim())
    .substringBefore('/')
    .takeIf { it.isNotBlank() }

private const val PUSH_MS = 220
private const val PUSH_FRACTION = 8
private const val PREDICTIVE_SCALE = 0.95f
