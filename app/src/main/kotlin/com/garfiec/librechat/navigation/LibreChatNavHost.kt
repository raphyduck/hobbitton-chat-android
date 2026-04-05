package com.garfiec.librechat.navigation

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import com.garfiec.librechat.MainActivity
import com.garfiec.librechat.R
import com.garfiec.librechat.core.ui.components.BannerDisplay
import com.garfiec.librechat.feature.agents.navigation.AgentMarketplace
import com.garfiec.librechat.feature.agents.navigation.agentsEntries
import com.garfiec.librechat.feature.auth.navigation.AuthRoute
import com.garfiec.librechat.feature.auth.navigation.ServerUrl
import com.garfiec.librechat.feature.auth.navigation.authEntries
import com.garfiec.librechat.feature.chat.navigation.Chat
import com.garfiec.librechat.feature.chat.navigation.NewChat
import com.garfiec.librechat.feature.chat.navigation.chatEntries
import com.garfiec.librechat.feature.conversations.navigation.ArchivedConversations
import com.garfiec.librechat.feature.conversations.navigation.conversationsEntries
import com.garfiec.librechat.feature.files.navigation.Files
import com.garfiec.librechat.feature.files.navigation.filesEntries
import com.garfiec.librechat.feature.settings.navigation.SettingsAccount
import com.garfiec.librechat.feature.settings.navigation.SettingsChat
import com.garfiec.librechat.feature.settings.navigation.SettingsData
import com.garfiec.librechat.feature.settings.navigation.SettingsGeneral
import com.garfiec.librechat.feature.settings.navigation.SettingsRoute
import com.garfiec.librechat.feature.settings.navigation.SettingsTabbed
import com.garfiec.librechat.feature.settings.navigation.memoriesEntry
import com.garfiec.librechat.feature.settings.navigation.mcpServersEntry
import com.garfiec.librechat.feature.settings.navigation.settingsEntries
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import co.touchlab.kermit.Logger

/** Maps a [SettingsCategory] to its corresponding typed navigation route. */
fun SettingsCategory.toRoute(): SettingsRoute = when (this) {
    SettingsCategory.GENERAL -> SettingsGeneral
    SettingsCategory.CHAT -> SettingsChat
    SettingsCategory.ACCOUNT -> SettingsAccount
    SettingsCategory.DATA -> SettingsData
}

@Composable
fun LibreChatNavHost(
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass? = null,
    deepLinkUri: Uri? = null,
    onDeepLinkConsumed: () -> Unit = {},
    shareNavigationTrigger: Int = 0,
    navHostViewModel: NavHostViewModel = koinViewModel(),
) {
    val isLoggedIn by navHostViewModel.isLoggedIn.collectAsStateWithLifecycle()

    // Stable start key — auth redirect handled via LaunchedEffect below.
    val backStack = rememberNavBackStack(navigationSavedStateConfig, NewChat)
    val navigator = Navigator(backStack)

    // Redirect to auth if not logged in (on first composition only).
    LaunchedEffect(Unit) {
        if (!navHostViewModel.isLoggedIn.value) {
            navigator.navigateToAuth()
        }
    }

    // Track active conversation from nav back stack
    LaunchedEffect(navigator.currentRoute) {
        val conversationId = (navigator.currentRoute as? Chat)?.conversationId
        navHostViewModel.setActiveConversation(conversationId)
    }

    // Handle session expiry
    LaunchedEffect(Unit) {
        navHostViewModel.sessionExpired.collect {
            navigator.navigateToAuth()
        }
    }

    val isTablet = windowSizeClass?.widthSizeClass?.let {
        it >= WindowWidthSizeClass.Medium
    } ?: false

    if (isTablet) {
        TabletLayout(
            navigator = navigator,
            navHostViewModel = navHostViewModel,
            deepLinkUri = deepLinkUri,
            onDeepLinkConsumed = onDeepLinkConsumed,
            shareNavigationTrigger = shareNavigationTrigger,
            modifier = modifier,
        )
    } else {
        PhoneLayout(
            navigator = navigator,
            navHostViewModel = navHostViewModel,
            deepLinkUri = deepLinkUri,
            onDeepLinkConsumed = onDeepLinkConsumed,
            shareNavigationTrigger = shareNavigationTrigger,
            modifier = modifier,
        )
    }

    // Version mismatch warning dialog (shown over any layout)
    val versionMismatch by navHostViewModel.versionMismatch.collectAsStateWithLifecycle()
    versionMismatch?.let { mismatch ->
        VersionMismatchDialog(
            supportedVersion = mismatch.supportedVersion,
            backendVersion = mismatch.backendVersion,
            onDismiss = navHostViewModel::dismissVersionWarning,
            onDismissPermanently = navHostViewModel::dismissVersionWarningPermanently,
        )
    }
}

/**
 * Dialog shown when the backend server version does not match the version
 * this app was built for. Offers "Dismiss" (session-only) and
 * "Don't warn again" (persisted per backend version) options.
 */
@Composable
private fun VersionMismatchDialog(
    supportedVersion: String,
    backendVersion: String,
    onDismiss: () -> Unit,
    onDismissPermanently: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.version_mismatch_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.version_mismatch_message, supportedVersion, backendVersion),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissPermanently) {
                Text(stringResource(R.string.dont_warn_again))
            }
        },
    )
}

@Composable
private fun PhoneLayout(
    navigator: Navigator,
    navHostViewModel: NavHostViewModel,
    deepLinkUri: Uri?,
    onDeepLinkConsumed: () -> Unit,
    shareNavigationTrigger: Int,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val banners by navHostViewModel.banners.collectAsStateWithLifecycle()
    val dismissedBannerIds by navHostViewModel.dismissedBannerIds.collectAsStateWithLifecycle()

    // Handle deep links
    LaunchedEffect(deepLinkUri) {
        deepLinkUri?.let { uri ->
            if (uri.scheme == "librechat" && uri.host == "conversation") {
                uri.lastPathSegment?.let { conversationId ->
                    if (MainActivity.CONVERSATION_ID_REGEX.matches(conversationId)) {
                        navigator.navigateToChat(conversationId)
                    } else {
                        Logger.w { "Ignoring deep link with invalid conversation ID: $conversationId" }
                    }
                }
            }
            onDeepLinkConsumed()
        }
    }

    // Handle share intent
    LaunchedEffect(shareNavigationTrigger) {
        if (shareNavigationTrigger > 0) {
            val currentRoute = navigator.currentRoute
            val isOnChatScreen = currentRoute is Chat || currentRoute is NewChat
            if (!isOnChatScreen) {
                navigator.navigateToTopLevel(NewChat)
            }
        }
    }

    // Reset sidebar mode to Conversations when the drawer closes
    LaunchedEffect(drawerState.isClosed) {
        if (drawerState.isClosed) {
            navHostViewModel.setSidebarMode(SidebarMode.Conversations)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !navigator.isInAuthFlow,
        drawerContent = {
            ModalDrawerSheet {
                SidebarScaffold(
                    viewModel = navHostViewModel,
                    onNewChat = {
                        scope.launch { drawerState.close() }
                        if (navigator.currentRoute !is NewChat) {
                            navigator.navigateToTopLevel(NewChat)
                        }
                    },
                    onConversationClick = { conversationId ->
                        scope.launch { drawerState.close() }
                        navigator.navigateToChat(conversationId)
                    },
                    onSettingsClick = {
                        scope.launch { drawerState.close() }
                        navigator.navigate(SettingsTabbed)
                    },
                    onSettingsCategorySelected = { category ->
                        navigator.navigate(category.toRoute())
                    },
                    onAgentsClick = {
                        scope.launch { drawerState.close() }
                        navigator.navigate(AgentMarketplace)
                    },
                    onFilesClick = {
                        scope.launch { drawerState.close() }
                        navigator.navigate(Files)
                    },
                )
            }
        },
    ) {
        Column(modifier = modifier.fillMaxSize()) {
            if (!navigator.isInAuthFlow && banners.isNotEmpty()) {
                BannerDisplay(
                    banners = banners,
                    dismissedIds = dismissedBannerIds,
                    onDismiss = navHostViewModel::dismissBanner,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            MainNavDisplay(
                navigator = navigator,
                navHostViewModel = navHostViewModel,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun MainNavDisplay(
    navigator: Navigator,
    navHostViewModel: NavHostViewModel,
    onOpenDrawer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = navigator.backStack,
        onBack = { navigator.goBack() },
        modifier = modifier,
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) togetherWith
                fadeOut(animationSpec = tween(150))
        },
        popTransitionSpec = {
            (fadeIn(initialAlpha = 0.5f, animationSpec = tween(300, easing = LinearEasing)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(300, easing = LinearEasing))) togetherWith
                (slideOutHorizontally(
                    targetOffsetX = { (it * 0.15f).toInt() },
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                ) + scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(300, easing = LinearEasing),
                ))
        },
        predictivePopTransitionSpec = {
            (fadeIn(initialAlpha = 0.5f, animationSpec = tween(300, easing = LinearEasing)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(300, easing = LinearEasing))) togetherWith
                (slideOutHorizontally(
                    targetOffsetX = { (it * 0.15f).toInt() },
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                ) + scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(300, easing = LinearEasing),
                ))
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            authEntries(
                onNavigate = { navigator.navigate(it) },
                onBack = { navigator.goBack() },
                onAuthComplete = {
                    navHostViewModel.onAuthComplete()
                    navigator.navigateToChat()
                },
            )
            chatEntries(
                onNavigate = { navigator.navigate(it) },
                onBack = { navigator.goBack() },
                onNavigateToChat = { navigator.navigateToChat(it) },
                onOpenDrawer = onOpenDrawer,
            )
            conversationsEntries(
                onConversationClick = { navigator.navigateToChat(it) },
                onNavigateToArchived = { navigator.navigate(ArchivedConversations) },
                onBack = { navigator.goBack() },
            )
            agentsEntries(
                onNavigate = { navigator.navigate(it) },
                onBack = { navigator.goBack() },
                onStartChat = { _ -> navigator.navigateToTopLevel(NewChat) },
            )
            filesEntries(
                onBack = { navigator.goBack() },
            )
            settingsEntries(
                onNavigate = { navigator.navigate(it) },
                onBack = { navigator.goBack() },
                onLogout = {
                    navHostViewModel.logout()
                    navigator.navigateToAuth()
                },
                onNavigateToArchived = { navigator.navigate(ArchivedConversations) },
            )
            memoriesEntry(
                onBack = { navigator.goBack() },
            )
            mcpServersEntry(
                onBack = { navigator.goBack() },
            )
        },
    )
}
