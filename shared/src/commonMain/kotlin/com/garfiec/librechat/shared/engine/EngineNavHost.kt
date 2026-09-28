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
import com.garfiec.librechat.core.ui.theme.AppLocale
import com.garfiec.librechat.core.ui.util.SafeUriHandler
import com.garfiec.librechat.feature.auth.screen.PortalSignInScreen
import com.garfiec.librechat.feature.tasks.navigation.EngineChat
import com.garfiec.librechat.feature.tasks.navigation.engineChatEntries
import com.garfiec.librechat.feature.tasks.navigation.tasksEntries
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app's root since D-077: one engine, two profiles.
 *
 * Signed out, the portal's sign-in ([PortalSignInScreen]); signed in, the chat — an engine session
 * on the `chat` profile — with a drawer of recent chats, the Tasks tab and the settings. Nothing of
 * LibreChat is reachable from here: no server URL, no LibreChat login, no agents marketplace, no
 * files, no LibreChat conversations, tags, sharing or account; and none of their view models, so
 * none of their startup calls (config, banners, version check, token refresh, session tasks).
 *
 * The upstream shell (`LibreChatNavHost`) still exists and still compiles — iOS starts from it, as
 * the engine graph is Android-only (D-034) — but Android no longer composes it.
 */
@Composable
fun EngineNavHost(
    modifier: Modifier = Modifier,
    appLocaleTag: String? = null,
    viewModel: EngineShellViewModel = koinViewModel(),
) {
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val addresses by viewModel.addresses.collectAsStateWithLifecycle()

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
                        settings = EngineSettingsUiState(themeMode = themeMode, addresses = addresses),
                        onRefreshChats = viewModel::refreshChats,
                        onThemeMode = viewModel::setThemeMode,
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
    onRefreshChats: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
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

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
    val activeChat = (navigator.currentRoute as? EngineChat)?.sessionId

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
                    onChatStarted = { sessionId, title ->
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
                    onOpenDrawer = openDrawer,
                )
                entry<EngineAppSettings> {
                    EngineSettingsScreen(
                        state = settings,
                        onBack = { navigator.goBack() },
                        onOpenUsage = { navigator.openUsage() },
                        onThemeMode = onThemeMode,
                        onSignOut = {
                            // The stack first: its conversations close with their entries — their
                            // view models, their live feeds — before the portal's tokens go.
                            navigator.newChat()
                            onSignOut()
                        },
                    )
                }
            },
        )
    }
}
