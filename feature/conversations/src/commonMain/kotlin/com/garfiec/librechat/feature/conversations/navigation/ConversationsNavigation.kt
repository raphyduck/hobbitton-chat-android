package com.garfiec.librechat.feature.conversations.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.conversations.screen.ArchivedConversationsScreen
import com.garfiec.librechat.feature.conversations.screen.ConversationListScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface ConversationsRoute : NavKey

@Serializable data object Conversations : ConversationsRoute
@Serializable data object ArchivedConversations : ConversationsRoute

fun EntryProviderScope<NavKey>.conversationsEntries(
    onConversationClick: (String) -> Unit,
    onNavigateToArchived: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    entry<Conversations> {
        ConversationListScreen(
            onConversationClick = onConversationClick,
            onNavigateToArchived = onNavigateToArchived,
        )
    }
    entry<ArchivedConversations> {
        ArchivedConversationsScreen(
            onNavigateBack = onBack,
        )
    }
}

val conversationsSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(Conversations::class, Conversations.serializer())
        subclass(ArchivedConversations::class, ArchivedConversations.serializer())
    }
}
