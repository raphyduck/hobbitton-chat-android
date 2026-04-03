package com.garfiec.librechat.shared.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Destinations accessible from the navigation drawer footer.
 * The primary navigation is sidebar-first (drawer with conversation list),
 * matching the web frontend pattern.
 */
enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
) {
    CHAT("chat_graph", Icons.AutoMirrored.Filled.Chat, "Chat"),
    CONVERSATIONS("conversations", Icons.Default.Forum, "Conversations"),
    AGENTS("agents", Icons.Default.SmartToy, "Agents"),
    FILES("files", Icons.Default.Folder, "Files"),
}
