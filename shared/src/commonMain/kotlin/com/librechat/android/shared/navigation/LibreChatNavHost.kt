package com.librechat.android.shared.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOut
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.savedstate.read
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.librechat.android.core.ui.components.BannerDisplay
import com.librechat.android.feature.agents.navigation.AGENT_EDITOR_CREATE_ROUTE
import com.librechat.android.feature.agents.navigation.agentsGraph
import com.librechat.android.feature.auth.navigation.AUTH_GRAPH_ROUTE
import com.librechat.android.feature.auth.navigation.authGraph
import com.librechat.android.feature.chat.navigation.CHAT_GRAPH_ROUTE
import com.librechat.android.feature.chat.navigation.CHAT_ROUTE
import com.librechat.android.feature.chat.navigation.NEW_CHAT_ROUTE
import com.librechat.android.feature.chat.navigation.chatGraph
import com.librechat.android.feature.chat.navigation.navigateToChat
import com.librechat.android.feature.conversations.navigation.conversationsGraph
import com.librechat.android.feature.files.navigation.filesGraph
import com.librechat.android.feature.settings.navigation.API_KEYS_ROUTE
import com.librechat.android.feature.settings.navigation.PRESET_MANAGER_ROUTE
import com.librechat.android.feature.settings.navigation.SETTINGS_ACCOUNT_ROUTE
import com.librechat.android.feature.settings.navigation.SETTINGS_CHAT_ROUTE
import com.librechat.android.feature.settings.navigation.SETTINGS_DATA_ROUTE
import com.librechat.android.feature.settings.navigation.SETTINGS_GENERAL_ROUTE
import com.librechat.android.feature.settings.navigation.SETTINGS_TABBED_ROUTE
import com.librechat.android.feature.settings.navigation.SHARED_LINKS_ROUTE
import com.librechat.android.feature.settings.navigation.settingsGraph
import kotlinx.coroutines.launch
import librechat_android.shared.generated.resources.Res
import librechat_android.shared.generated.resources.dismiss
import librechat_android.shared.generated.resources.dont_warn_again
import librechat_android.shared.generated.resources.version_mismatch_message
import librechat_android.shared.generated.resources.version_mismatch_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Maps a [SettingsCategory] to its corresponding navigation route. */
fun SettingsCategory.toRoute(): String = when (this) {
    SettingsCategory.GENERAL -> SETTINGS_GENERAL_ROUTE
    SettingsCategory.CHAT -> SETTINGS_CHAT_ROUTE
    SettingsCategory.ACCOUNT -> SETTINGS_ACCOUNT_ROUTE
    SettingsCategory.DATA -> SETTINGS_DATA_ROUTE
}

/**
 * Shared root composable for LibreChat navigation.
 * Uses ModalNavigationDrawer (phone layout) for the sidebar-first pattern.
 *
 * Platform-specific features (deep links, tablet layout with BackHandler + swipe)
 * are handled by the Android app module. This shared version provides the core
 * phone layout that works on both Android and iOS.
 */
@Composable
fun LibreChatNavHost(
    modifier: Modifier = Modifier,
    navHostViewModel: NavHostViewModel = koinViewModel(),
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isLoggedIn by navHostViewModel.isLoggedIn.collectAsStateWithLifecycle()

    val isInAuthFlow = currentDestination?.route == AUTH_GRAPH_ROUTE ||
        currentDestination?.parent?.route == AUTH_GRAPH_ROUTE

    // Track active conversation from nav back stack.
    // Use SavedState.read {} to extract nav args (KMP-compatible, no Bundle.getString).
    LaunchedEffect(navBackStackEntry) {
        val destRoute = navBackStackEntry?.destination?.route
        val conversationId = if (destRoute == CHAT_ROUTE) {
            navBackStackEntry?.arguments?.read { getStringOrNull("conversationId") }
        } else {
            null
        }
        navHostViewModel.setActiveConversation(conversationId)
    }

    // Handle session expiry
    LaunchedEffect(Unit) {
        navHostViewModel.sessionExpired.collect {
            navController.navigate(AUTH_GRAPH_ROUTE) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val startDestination = if (isLoggedIn) CHAT_GRAPH_ROUTE else AUTH_GRAPH_ROUTE

    PhoneLayout(
        navController = navController,
        navHostViewModel = navHostViewModel,
        startDestination = startDestination,
        isInAuthFlow = isInAuthFlow,
        modifier = modifier,
    )

    // Version mismatch warning dialog
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
                text = stringResource(Res.string.version_mismatch_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.version_mismatch_message, supportedVersion, backendVersion),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dismiss))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissPermanently) {
                Text(stringResource(Res.string.dont_warn_again))
            }
        },
    )
}

@Composable
private fun PhoneLayout(
    navController: androidx.navigation.NavHostController,
    navHostViewModel: NavHostViewModel,
    startDestination: String,
    isInAuthFlow: Boolean,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val banners by navHostViewModel.banners.collectAsStateWithLifecycle()
    val dismissedBannerIds by navHostViewModel.dismissedBannerIds.collectAsStateWithLifecycle()

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
                        val currentRoute = navController.currentBackStackEntry?.destination?.route
                        if (currentRoute != NEW_CHAT_ROUTE) {
                            navController.navigate(NEW_CHAT_ROUTE) {
                                popUpTo(CHAT_GRAPH_ROUTE) { inclusive = false }
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
                        navController.navigate(SETTINGS_TABBED_ROUTE) {
                            launchSingleTop = true
                        }
                    },
                    onSettingsCategorySelected = { category ->
                        navController.navigate(category.toRoute()) {
                            launchSingleTop = true
                        }
                    },
                    onAgentsClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(TopLevelDestination.AGENTS.route) {
                            launchSingleTop = true
                        }
                    },
                    onFilesClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(TopLevelDestination.FILES.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        },
    ) {
        Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                        navController.navigate(CHAT_GRAPH_ROUTE) {
                            popUpTo(AUTH_GRAPH_ROUTE) { inclusive = true }
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
                        navController.navigate("conversations/archived")
                    },
                    onNavigateBackFromArchived = {
                        navController.popBackStack()
                    },
                )
                agentsGraph(
                    onAgentClick = { agentId ->
                        navController.navigate("agents/$agentId")
                    },
                    onBack = { navController.popBackStack() },
                    onStartChat = { agentId ->
                        navController.navigate(NEW_CHAT_ROUTE) {
                            popUpTo(CHAT_GRAPH_ROUTE) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onCreateAgent = {
                        navController.navigate(AGENT_EDITOR_CREATE_ROUTE)
                    },
                    onEditAgent = { agentId ->
                        navController.navigate("agents/editor/$agentId")
                    },
                )
                filesGraph(
                    onBack = { navController.popBackStack() },
                )
                settingsGraph(
                    onLogout = {
                        navHostViewModel.logout()
                        navController.navigate(AUTH_GRAPH_ROUTE) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToArchived = {
                        navController.navigate("conversations/archived") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSharedLinks = {
                        navController.navigate(SHARED_LINKS_ROUTE) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateBackFromSharedLinks = {
                        navController.popBackStack()
                    },
                    onNavigateToPresets = {
                        navController.navigate(PRESET_MANAGER_ROUTE) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateBackFromPresets = {
                        navController.popBackStack()
                    },
                    onNavigateToApiKeys = {
                        navController.navigate(API_KEYS_ROUTE) {
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
