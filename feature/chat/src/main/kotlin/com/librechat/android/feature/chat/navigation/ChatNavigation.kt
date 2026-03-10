package com.librechat.android.feature.chat.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.librechat.android.core.ui.components.ScreenTransitionWrapper
import com.librechat.android.feature.chat.prompts.PromptEditorScreen
import com.librechat.android.feature.chat.prompts.PromptsLibraryScreen
import com.librechat.android.feature.chat.screen.ChatScreen
import com.librechat.android.feature.chat.screen.NewChatScreen

const val CHAT_GRAPH_ROUTE = "chat_graph"
const val NEW_CHAT_ROUTE = "new_chat"
const val CHAT_ROUTE = "chat/{conversationId}"
const val PROMPTS_LIBRARY_ROUTE = "prompts_library"
const val PROMPT_EDITOR_ROUTE = "prompt_editor?groupId={groupId}"

fun NavController.navigateToChat(conversationId: String) {
    val currentEntry = currentBackStackEntry
    val isCurrentlyInChat = currentEntry?.destination?.route == CHAT_ROUTE
    val currentConvoId = currentEntry?.arguments?.getString("conversationId")

    // Already viewing this exact conversation -- no-op to avoid reload/flash
    if (isCurrentlyInChat && currentConvoId == conversationId) return

    val currentDestId = currentEntry?.destination?.id
    navigate("chat/$conversationId") {
        if (isCurrentlyInChat && currentDestId != null) {
            // Already viewing a chat -- replace it so switching chats
            // doesn't stack entries. Back will go to whatever was
            // before the first chat (e.g. new_chat, settings, agents).
            popUpTo(currentDestId) { inclusive = true }
        }
        // From non-chat screens just push onto the backstack normally
        launchSingleTop = true
    }
}

fun NavController.navigateToPromptsLibrary() {
    navigate(PROMPTS_LIBRARY_ROUTE)
}

fun NavController.navigateToPromptEditor(groupId: String? = null) {
    if (groupId != null) {
        navigate("prompt_editor?groupId=$groupId")
    } else {
        navigate("prompt_editor")
    }
}

fun NavGraphBuilder.chatGraph(
    navController: NavController,
    onOpenDrawer: (() -> Unit)? = null,
) {
    navigation(startDestination = NEW_CHAT_ROUTE, route = CHAT_GRAPH_ROUTE) {
        composable(
            route = NEW_CHAT_ROUTE,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { null },
            popExitTransition = { null },
        ) {
            ScreenTransitionWrapper(transition) {
                NewChatScreen(
                    onConversationStarted = { conversationId ->
                        // Navigate to chat/{id} immediately when the conversationId
                        // is known (at StreamEvent.Created). The new ChatViewModel
                        // will resume the active stream. The new_chat landing page
                        // stays clean in the back stack.
                        navController.navigateToChat(conversationId)
                    },
                    onOpenDrawer = onOpenDrawer,
                    onNavigateToPromptsLibrary = { navController.navigateToPromptsLibrary() },
                )
            }
        }
        composable(
            route = CHAT_ROUTE,
            arguments = listOf(
                navArgument("conversationId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { null },
            popExitTransition = { null },
        ) {
            ScreenTransitionWrapper(transition) {
                ChatScreen(
                    onOpenDrawer = onOpenDrawer,
                    onNavigateToPromptsLibrary = { navController.navigateToPromptsLibrary() },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToConversation = { navController.navigateToChat(it) },
                )
            }
        }
        composable(PROMPTS_LIBRARY_ROUTE) {
            ScreenTransitionWrapper(transition) {
                PromptsLibraryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onUseInChat = { promptText ->
                        navController.popBackStack()
                        // Navigate to new chat (the prompt text would be inserted via shared state)
                    },
                    onNavigateToEditor = { groupId ->
                        navController.navigateToPromptEditor(groupId)
                    },
                )
            }
        }
        composable(
            route = PROMPT_EDITOR_ROUTE,
            arguments = listOf(
                navArgument("groupId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ScreenTransitionWrapper(transition) {
                PromptEditorScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
