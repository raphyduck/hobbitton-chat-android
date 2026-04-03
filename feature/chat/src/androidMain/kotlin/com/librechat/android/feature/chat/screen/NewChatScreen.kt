package com.librechat.android.feature.chat.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Thin wrapper that shows ChatScreen with no conversationId.
 * When a conversation starts via streaming, the [onConversationStarted]
 * callback is invoked with the new conversationId, allowing navigation
 * to the full chat route.
 */
@Composable
actual fun NewChatScreen(
    onConversationStarted: (String) -> Unit,
    modifier: Modifier,
    onOpenDrawer: (() -> Unit)?,
    onNavigateToPromptsLibrary: (() -> Unit)?,
) {
    ChatScreen(
        modifier = modifier,
        onConversationStarted = onConversationStarted,
        onOpenDrawer = onOpenDrawer,
        onNavigateToPromptsLibrary = onNavigateToPromptsLibrary,
    )
}
