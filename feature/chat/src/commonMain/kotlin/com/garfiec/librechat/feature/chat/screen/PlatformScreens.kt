package com.garfiec.librechat.feature.chat.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Platform-specific chat screen implementation. */
@Composable
expect fun ChatScreen(
    modifier: Modifier = Modifier,
    conversationId: String? = null,
    onConversationStart: ((String) -> Unit)? = null,
    onNavigateToConversation: ((String) -> Unit)? = null,
    onOpenDrawer: (() -> Unit)? = null,
    onNavigateToPromptsLibrary: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
)

/** Platform-specific new chat screen. */
@Composable
expect fun NewChatScreen(
    onConversationStart: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
    onNavigateToPromptsLibrary: (() -> Unit)? = null,
)
