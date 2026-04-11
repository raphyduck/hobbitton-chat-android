package com.garfiec.librechat.feature.chat.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.chat.prompts.PromptEditorScreen
import com.garfiec.librechat.feature.chat.prompts.PromptsLibraryScreen
import com.garfiec.librechat.feature.chat.screen.ChatScreen
import com.garfiec.librechat.feature.chat.screen.NewChatScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface ChatRoute : NavKey

@Serializable data object NewChat : ChatRoute

@Serializable data class Chat(val conversationId: String? = null) : ChatRoute

@Serializable data object PromptsLibrary : ChatRoute

@Serializable data class PromptEditor(val groupId: String? = null) : ChatRoute

fun EntryProviderScope<NavKey>.chatEntries(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
) {
    entry<NewChat> {
        NewChatScreen(
            onConversationStart = { conversationId ->
                onNavigateToChat(conversationId)
            },
            onOpenDrawer = onOpenDrawer,
            onNavigateToPromptsLibrary = { onNavigate(PromptsLibrary) },
        )
    }
    entry<Chat> { key ->
        ChatScreen(
            conversationId = key.conversationId,
            onOpenDrawer = onOpenDrawer,
            onNavigateToPromptsLibrary = { onNavigate(PromptsLibrary) },
            onNavigateBack = onBack,
            onNavigateToConversation = { conversationId -> onNavigateToChat(conversationId) },
        )
    }
    entry<PromptsLibrary> {
        PromptsLibraryScreen(
            onNavigateBack = onBack,
            onUseInChat = { _ -> onBack() },
            onNavigateToEditor = { groupId ->
                onNavigate(PromptEditor(groupId = groupId))
            },
        )
    }
    entry<PromptEditor> { key ->
        PromptEditorScreen(
            onBack = onBack,
            groupId = key.groupId,
        )
    }
}

val chatSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(NewChat::class, NewChat.serializer())
        subclass(Chat::class, Chat.serializer())
        subclass(PromptsLibrary::class, PromptsLibrary.serializer())
        subclass(PromptEditor::class, PromptEditor.serializer())
    }
}
