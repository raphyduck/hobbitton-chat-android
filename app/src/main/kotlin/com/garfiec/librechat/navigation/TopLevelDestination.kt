package com.garfiec.librechat.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Destinations accessible from the navigation drawer footer.
 * The primary navigation is now sidebar-first (drawer with conversation list),
 * matching the web frontend pattern. Bottom nav has been removed entirely.
 *
 * Conversations are integrated into the drawer body (not a separate destination).
 * Agents and Files are accessible via drawer footer links.
 * Settings are accessed through the sidebar settings nav menu (see [SettingsCategory]).
 */
enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
) {
    CHAT("chat_graph", Icons.Default.Chat, "Chat"),
    CONVERSATIONS("conversations", Icons.Default.Forum, "Conversations"),
    AGENTS("agents", Icons.Default.SmartToy, "Agents"),
    FILES("files", Icons.Default.Folder, "Files"),
}
