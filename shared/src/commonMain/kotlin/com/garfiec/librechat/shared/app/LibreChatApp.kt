package com.garfiec.librechat.shared.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.garfiec.librechat.core.data.datastore.ThemeDataStore
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.shared.navigation.LibreChatNavHost
import org.koin.compose.koinInject

/**
 * Root composable for the CMP app (used by iOS via MainViewController).
 *
 * Provides the full navigation shell with drawer-based sidebar matching
 * the web frontend pattern: conversation list, search, favorites, and
 * footer links to Agents, Files, and Settings.
 *
 * Auth is handled within the navigation graph — if not logged in, the
 * auth graph is shown as the start destination.
 */
@Composable
fun LibreChatApp() {
    val themeDataStore = koinInject<ThemeDataStore>()
    val themeMode by themeDataStore.themeMode.collectAsState(initial = themeDataStore.initialThemeMode)
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    LibreChatTheme(darkTheme = darkTheme) {
        LibreChatNavHost()
    }
}
