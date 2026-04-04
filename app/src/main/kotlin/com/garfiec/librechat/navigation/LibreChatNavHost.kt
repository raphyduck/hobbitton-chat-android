package com.garfiec.librechat.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOut
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.toRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlin.reflect.KClass
import com.garfiec.librechat.MainActivity
import com.garfiec.librechat.R
import com.garfiec.librechat.core.ui.components.BannerDisplay
import com.garfiec.librechat.feature.agents.navigation.AgentDetail
import com.garfiec.librechat.feature.agents.navigation.AgentEditorCreate
import com.garfiec.librechat.feature.agents.navigation.AgentEditorEdit
import com.garfiec.librechat.feature.agents.navigation.AgentMarketplace
import com.garfiec.librechat.feature.agents.navigation.agentsGraph
import com.garfiec.librechat.feature.auth.navigation.AuthRoute
import com.garfiec.librechat.feature.auth.navigation.ServerUrl
import com.garfiec.librechat.feature.auth.navigation.authGraph
import com.garfiec.librechat.feature.chat.navigation.Chat
import com.garfiec.librechat.feature.chat.navigation.ChatRoute
import com.garfiec.librechat.feature.chat.navigation.NewChat
import com.garfiec.librechat.feature.chat.navigation.chatGraph
import com.garfiec.librechat.feature.chat.navigation.navigateToChat
import com.garfiec.librechat.feature.conversations.navigation.ArchivedConversations
import com.garfiec.librechat.feature.conversations.navigation.conversationsGraph
import com.garfiec.librechat.feature.files.navigation.Files
import com.garfiec.librechat.feature.files.navigation.filesGraph
import com.garfiec.librechat.feature.settings.navigation.ApiKeys
import com.garfiec.librechat.feature.settings.navigation.PresetManager
import com.garfiec.librechat.feature.settings.navigation.SettingsAccount
import com.garfiec.librechat.feature.settings.navigation.SettingsChat
import com.garfiec.librechat.feature.settings.navigation.SettingsData
import com.garfiec.librechat.feature.settings.navigation.SettingsGeneral
import com.garfiec.librechat.feature.settings.navigation.SettingsRoute
import com.garfiec.librechat.feature.settings.navigation.SettingsTabbed
import com.garfiec.librechat.feature.settings.navigation.SharedLinks
import com.garfiec.librechat.feature.settings.navigation.settingsGraph
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import co.touchlab.kermit.Logger

/** Checks if this destination's route matches the given typed route class. */
private fun NavDestination?.isRoute(routeClass: KClass<*>): Boolean {
    val qualifiedName = routeClass.qualifiedName ?: return false
    return this?.route?.startsWith(qualifiedName) == true
}

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
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isLoggedIn by navHostViewModel.isLoggedIn.collectAsStateWithLifecycle()

    val isInAuthFlow = currentDestination.isRoute(AuthRoute::class) ||
        currentDestination?.parent.isRoute(AuthRoute::class)

    // Track active conversation from nav back stack
    LaunchedEffect(navBackStackEntry) {
        val conversationId = if (navBackStackEntry?.destination.isRoute(Chat::class)) {
            navBackStackEntry?.toRoute<Chat>()?.conversationId
        } else {
            null
        }
        navHostViewModel.setActiveConversation(conversationId)
    }

    // Handle session expiry
    LaunchedEffect(Unit) {
        navHostViewModel.sessionExpired.collect {
            navController.navigate(ServerUrl) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val startDestination: Any = if (isLoggedIn) ChatRoute::class else AuthRoute::class

    val isTablet = windowSizeClass?.widthSizeClass?.let {
        it >= WindowWidthSizeClass.Medium
    } ?: false

    if (isTablet) {
        TabletLayout(
            navController = navController,
            navHostViewModel = navHostViewModel,
            startDestination = startDestination,
            isInAuthFlow = isInAuthFlow,
            deepLinkUri = deepLinkUri,
            onDeepLinkConsumed = onDeepLinkConsumed,
            shareNavigationTrigger = shareNavigationTrigger,
            modifier = modifier,
        )
    } else {
        PhoneLayout(
            navController = navController,
            navHostViewModel = navHostViewModel,
            startDestination = startDestination,
            isInAuthFlow = isInAuthFlow,
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
    navController: androidx.navigation.NavHostController,
    navHostViewModel: NavHostViewModel,
    startDestination: Any,
    isInAuthFlow: Boolean,
    deepLinkUri: Uri?,
    onDeepLinkConsumed: () -> Unit,
    shareNavigationTrigger: Int,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Banner state only -- drawer state is collected inside DrawerContent itself
    val banners by navHostViewModel.banners.collectAsStateWithLifecycle()
    val dismissedBannerIds by navHostViewModel.dismissedBannerIds.collectAsStateWithLifecycle()

    // Handle deep links
    LaunchedEffect(deepLinkUri) {
        deepLinkUri?.let { uri ->
            if (uri.scheme == "librechat" && uri.host == "conversation") {
                uri.lastPathSegment?.let { conversationId ->
                    if (MainActivity.CONVERSATION_ID_REGEX.matches(conversationId)) {
                        navController.navigateToChat(conversationId)
                    } else {
                        Logger.w { "Ignoring deep link with invalid conversation ID: $conversationId" }
                    }
                }
            }
            onDeepLinkConsumed()
        }
    }

    // Handle share intent: if already on a chat screen, let the active ChatViewModel
    // consume the shared content reactively. Only navigate to new chat when on a
    // non-chat screen (e.g. settings, agents, files).
    LaunchedEffect(shareNavigationTrigger) {
        if (shareNavigationTrigger > 0) {
            val currentDest = navController.currentBackStackEntry?.destination
            val isOnChatScreen = currentDest.isRoute(Chat::class) ||
                currentDest.isRoute(NewChat::class)
            if (!isOnChatScreen) {
                navController.navigate(NewChat) {
                    popUpTo<ChatRoute> { inclusive = false }
                    launchSingleTop = true
                }
            }
            // If already on a chat screen, SharedIntentConsumer.shareAvailable
            // will notify the active ChatViewModel to consume the content.
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
        gesturesEnabled = !isInAuthFlow,
        drawerContent = {
            ModalDrawerSheet {
                SidebarScaffold(
                    viewModel = navHostViewModel,
                    onNewChat = {
                        scope.launch { drawerState.close() }
                        // Skip navigation if already on the new chat screen
                        val currentDest = navController.currentBackStackEntry?.destination
                        if (!currentDest.isRoute(NewChat::class)) {
                            navController.navigate(NewChat) {
                                popUpTo<ChatRoute> { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    },
                    onConversationClick = { conversationId ->
                        scope.launch { drawerState.close() }
                        navController.navigateToChat(conversationId)
                    },
                    onSettingsClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(SettingsTabbed) {
                            launchSingleTop = true
                        }
                    },
                    onSettingsCategorySelected = { category ->
                        // Navigate to the settings sub-page but keep the drawer open
                        navController.navigate(category.toRoute()) {
                            launchSingleTop = true
                        }
                    },
                    onAgentsClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(AgentMarketplace) {
                            launchSingleTop = true
                        }
                    },
                    onFilesClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(Files) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        },
    ) {
        // No bottom bar -- drawer is the primary navigation
        Column(modifier = modifier.fillMaxSize()) {
            if (!isInAuthFlow && banners.isNotEmpty()) {
                BannerDisplay(
                    banners = banners,
                    dismissedIds = dismissedBannerIds,
                    onDismiss = navHostViewModel::dismissBanner,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300),
                    )
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(150))
                },
                popEnterTransition = {
                    fadeIn(
                        initialAlpha = 0.5f,
                        animationSpec = tween(300, easing = LinearEasing),
                    ) + scaleIn(
                        initialScale = 0.92f,
                        animationSpec = tween(300, easing = LinearEasing),
                    )
                },
                popExitTransition = {
                    slideOut(
                        targetOffset = { IntOffset((it.width * 0.15f).toInt(), 0) },
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                    ) + scaleOut(
                        targetScale = 0.92f,
                        animationSpec = tween(300, easing = LinearEasing),
                    )
                },
            ) {
                authGraph(
                    navController = navController,
                    onAuthComplete = {
                        navHostViewModel.onAuthComplete()
                        navController.navigate(NewChat) {
                            popUpTo<AuthRoute> { inclusive = true }
                        }
                    },
                )
                chatGraph(
                    navController = navController,
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    },
                )
                conversationsGraph(
                    onConversationClick = { conversationId ->
                        navController.navigateToChat(conversationId)
                    },
                    onNavigateToArchived = {
                        navController.navigate(ArchivedConversations)
                    },
                    onNavigateBackFromArchived = {
                        navController.popBackStack()
                    },
                )
                agentsGraph(
                    onAgentClick = { agentId ->
                        navController.navigate(AgentDetail(agentId = agentId))
                    },
                    onBack = { navController.popBackStack() },
                    onStartChat = { agentId ->
                        navController.navigate(NewChat) {
                            popUpTo<ChatRoute> { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onCreateAgent = {
                        navController.navigate(AgentEditorCreate)
                    },
                    onEditAgent = { agentId ->
                        navController.navigate(AgentEditorEdit(agentId = agentId))
                    },
                )
                filesGraph()
                settingsGraph(
                    onLogout = {
                        navHostViewModel.logout()
                        navController.navigate(ServerUrl) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToArchived = {
                        navController.navigate(ArchivedConversations) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSharedLinks = {
                        navController.navigate(SharedLinks) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateBackFromSharedLinks = {
                        navController.popBackStack()
                    },
                    onNavigateToPresets = {
                        navController.navigate(PresetManager) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateBackFromPresets = {
                        navController.popBackStack()
                    },
                    onNavigateToApiKeys = {
                        navController.navigate(ApiKeys) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateBackFromApiKeys = {
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}
