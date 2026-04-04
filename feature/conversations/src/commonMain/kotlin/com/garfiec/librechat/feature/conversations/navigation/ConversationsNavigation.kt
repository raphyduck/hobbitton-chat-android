package com.garfiec.librechat.feature.conversations.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.garfiec.librechat.core.ui.components.ScreenTransitionWrapper
import com.garfiec.librechat.feature.conversations.screen.ArchivedConversationsScreen
import com.garfiec.librechat.feature.conversations.screen.ConversationListScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface ConversationsRoute

@Serializable data object Conversations : ConversationsRoute
@Serializable data object ArchivedConversations : ConversationsRoute

fun NavGraphBuilder.conversationsGraph(
    onConversationClick: (String) -> Unit,
    onNavigateToArchived: () -> Unit = {},
    onNavigateBackFromArchived: () -> Unit = {},
) {
    composable<Conversations> {
        ScreenTransitionWrapper(transition) {
            ConversationListScreen(
                onConversationClick = onConversationClick,
                onNavigateToArchived = onNavigateToArchived,
            )
        }
    }
    composable<ArchivedConversations> {
        ScreenTransitionWrapper(transition) {
            ArchivedConversationsScreen(
                onNavigateBack = onNavigateBackFromArchived,
            )
        }
    }
}

val conversationsSerializersModule = SerializersModule {
    polymorphic(ConversationsRoute::class) {
        subclass(Conversations::class, Conversations.serializer())
        subclass(ArchivedConversations::class, ArchivedConversations.serializer())
    }
}
