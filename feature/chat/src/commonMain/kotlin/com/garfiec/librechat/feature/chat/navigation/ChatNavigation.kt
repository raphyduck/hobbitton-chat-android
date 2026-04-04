package com.garfiec.librechat.feature.chat.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlin.reflect.KClass
import com.garfiec.librechat.feature.chat.prompts.PromptEditorScreen
import com.garfiec.librechat.feature.chat.prompts.PromptsLibraryScreen
import com.garfiec.librechat.feature.chat.screen.ChatScreen
import com.garfiec.librechat.feature.chat.screen.NewChatScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface ChatRoute

@Serializable data object NewChat : ChatRoute
@Serializable data class Chat(val conversationId: String? = null) : ChatRoute
@Serializable data object PromptsLibrary : ChatRoute
@Serializable data class PromptEditor(val groupId: String? = null) : ChatRoute

/** Checks if this destination's route matches the given typed route class. */
internal fun NavDestination?.isRoute(routeClass: KClass<*>): Boolean {
    val qualifiedName = routeClass.qualifiedName ?: return false
    return this?.route?.startsWith(qualifiedName) == true
}

fun NavController.navigateToChat(conversationId: String) {
    val currentEntry = currentBackStackEntry
    val isCurrentlyInChat = currentEntry?.destination.isRoute(Chat::class)
    val currentDestId = currentEntry?.destination?.id
    navigate(Chat(conversationId = conversationId)) {
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
    navigate(PromptsLibrary)
}

fun NavController.navigateToPromptEditor(groupId: String? = null) {
    navigate(PromptEditor(groupId = groupId))
}

fun NavGraphBuilder.chatGraph(
    navController: NavController,
    onOpenDrawer: (() -> Unit)? = null,
) {
    navigation<ChatRoute>(startDestination = NewChat::class) {
        composable<NewChat>(
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
        composable<Chat>(
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
        composable<PromptsLibrary> {
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
        composable<PromptEditor> {
            PromptEditorScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

val chatSerializersModule = SerializersModule {
    polymorphic(ChatRoute::class) {
        subclass(NewChat::class, NewChat.serializer())
        subclass(Chat::class, Chat.serializer())
        subclass(PromptsLibrary::class, PromptsLibrary.serializer())
        subclass(PromptEditor::class, PromptEditor.serializer())
    }
}
