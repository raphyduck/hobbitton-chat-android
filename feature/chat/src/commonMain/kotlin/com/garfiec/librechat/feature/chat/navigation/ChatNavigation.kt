package com.garfiec.librechat.feature.chat.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.garfiec.librechat.feature.chat.prompts.PromptEditorScreen
import com.garfiec.librechat.feature.chat.prompts.PromptsLibraryScreen
import com.garfiec.librechat.feature.chat.screen.ChatScreen
import com.garfiec.librechat.feature.chat.screen.NewChatScreen

const val CHAT_GRAPH_ROUTE = "chat_graph"
const val NEW_CHAT_ROUTE = "new_chat"
const val CHAT_ROUTE = "chat/{conversationId}"
const val PROMPTS_LIBRARY_ROUTE = "prompts_library"
const val PROMPT_EDITOR_ROUTE = "prompt_editor?groupId={groupId}"

fun NavController.navigateToChat(conversationId: String) {
    val currentEntry = currentBackStackEntry
    val isCurrentlyInChat = currentEntry?.destination?.route == CHAT_ROUTE
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
            NewChatScreen(
                onConversationStarted = { conversationId ->
                    navController.navigateToChat(conversationId)
                },
                onOpenDrawer = onOpenDrawer,
                onNavigateToPromptsLibrary = { navController.navigateToPromptsLibrary() },
            )
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
        ) { _ ->
            ChatScreen(
                onOpenDrawer = onOpenDrawer,
                onNavigateToPromptsLibrary = { navController.navigateToPromptsLibrary() },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToConversation = { conversationId -> navController.navigateToChat(conversationId) },
            )
        }
        composable(PROMPTS_LIBRARY_ROUTE) {
            PromptsLibraryScreen(
                onNavigateBack = { navController.popBackStack() },
                onUseInChat = { promptText ->
                    navController.popBackStack()
                },
                onNavigateToEditor = { groupId ->
                    navController.navigateToPromptEditor(groupId)
                },
            )
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
            PromptEditorScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
