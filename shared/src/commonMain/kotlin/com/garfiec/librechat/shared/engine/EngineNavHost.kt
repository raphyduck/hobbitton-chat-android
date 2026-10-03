package com.garfiec.librechat.shared.engine

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
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.data.engine.EngineSessionKind
import com.garfiec.librechat.core.data.engine.OpenConversation
import com.garfiec.librechat.core.ui.theme.AppLocale
import com.garfiec.librechat.core.ui.util.SafeUriHandler
import com.garfiec.librechat.feature.auth.screen.PortalSignInScreen
import com.garfiec.librechat.feature.tasks.navigation.EngineChat
import com.garfiec.librechat.feature.tasks.navigation.MissionChat
import com.garfiec.librechat.feature.tasks.navigation.engineChatEntries
import com.garfiec.librechat.feature.tasks.navigation.tasksEntries
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
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val addresses by viewModel.addresses.collectAsStateWithLifecycle()
    val attentionSound by viewModel.attentionSound.collectAsStateWithLifecycle()
    val openRequest by viewModel.openRequest.collectAsStateWithLifecycle()

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
                    true -> EngineMainLayout(
                        chats = chats,
                        settings = EngineSettingsUiState(
                            themeMode = themeMode,
                            addresses = addresses,
                            attentionSound = attentionSound,
                        ),
                        openRequest = openRequest,
                        onConsumeOpenRequest = viewModel::consumeOpenRequest,
                        onRefreshChats = viewModel::refreshChats,
                        onThemeMode = viewModel::setThemeMode,
                        onAttentionSound = { enabled ->
                            viewModel.setAttentionSound(enabled)
                            if (enabled) onRequestNotificationPermission()
                        },
                        onSignOut = viewModel::signOut,
                    )
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
    settings: EngineSettingsUiState,
    openRequest: OpenConversation?,
    onConsumeOpenRequest: (OpenConversation) -> Unit,
    onRefreshChats: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onAttentionSound: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(engineShellSavedStateConfig, EngineChat())
    val navigator = remember(backStack) { EngineNavigator(backStack) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val refreshChats by rememberUpdatedState(onRefreshChats)

    // The drawer re-reads the chats each time it opens: a chat started on another device, or
    // answered while the drawer was shut, moves to the top.
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) refreshChats()
    }

    // A tapped notification: its conversation as the root, as the drawer would open it. A session
    // this device never recorded opens as a task: the screen reads its agent off the transcript.
    val consumeOpenRequest by rememberUpdatedState(onConsumeOpenRequest)
    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        val title = request.title.orEmpty()
        when (request.kind) {
            EngineSessionKind.CHAT -> navigator.openChat(request.sessionId, title)
            EngineSessionKind.TASK, null -> navigator.openTask(request.sessionId, title)
        }
        drawerState.close()
        consumeOpenRequest(request)
    }

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
    val activeChat = when (val route = navigator.currentRoute) {
        is EngineChat -> route.sessionId
        is MissionChat -> route.sessionId
        else -> null
    }

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
                        when (chat.kind) {
                            EngineSessionKind.CHAT -> navigator.openChat(chat.sessionId, chat.title)
                            EngineSessionKind.TASK -> navigator.openTask(chat.sessionId, chat.title)
                        }
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
                )
            }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.goBack() },
            modifier = Modifier.fillMaxSize(),
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
                    onOpenMissionChat = { sessionId, title -> navigator.openMission(sessionId, title) },
                    onBack = { navigator.goBack() },
                    onOpenMissionRuns = { name -> navigator.openMissionRuns(name) },
                    onNewTask = { navigator.newTask() },
                    onTaskStart = { sessionId, title ->
                        navigator.taskStarted(sessionId, title)
                        refreshChats()
                    },
                    onOpenDrawer = openDrawer,
                )
                entry<EngineAppSettings> {
                    EngineSettingsScreen(
                        state = settings,
                        onBack = { navigator.goBack() },
                        onOpenInstructions = { navigator.openInstructions() },
                        onOpenUsage = { navigator.openUsage() },
                        onThemeMode = onThemeMode,
                        onAttentionSound = onAttentionSound,
                        onSignOut = {
                            // The stack first: its conversations close with their entries — their
                            // view models, their live feeds — before the portal's tokens go.
                            navigator.newChat()
                            onSignOut()
                        },
                    )
                }
                entry<EngineInstructions> {
                    EngineInstructionsScreen(onClose = { navigator.goBack() })
                }
            },
        )
    }
}
