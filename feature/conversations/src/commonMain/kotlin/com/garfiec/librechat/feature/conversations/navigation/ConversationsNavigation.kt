package com.garfiec.librechat.feature.conversations.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.garfiec.librechat.core.ui.components.ScreenTransitionWrapper
import com.garfiec.librechat.feature.conversations.screen.ArchivedConversationsScreen
import com.garfiec.librechat.feature.conversations.screen.ConversationListScreen

const val CONVERSATIONS_ROUTE = "conversations"
const val ARCHIVED_ROUTE = "conversations/archived"

fun NavGraphBuilder.conversationsGraph(
    onConversationClick: (String) -> Unit,
    onNavigateToArchived: () -> Unit = {},
    onNavigateBackFromArchived: () -> Unit = {},
) {
    composable(CONVERSATIONS_ROUTE) {
        ScreenTransitionWrapper(transition) {
            ConversationListScreen(
                onConversationClick = onConversationClick,
                onNavigateToArchived = onNavigateToArchived,
            )
        }
    }
    composable(ARCHIVED_ROUTE) {
        ScreenTransitionWrapper(transition) {
            ArchivedConversationsScreen(
                onNavigateBack = onNavigateBackFromArchived,
            )
        }
    }
}
