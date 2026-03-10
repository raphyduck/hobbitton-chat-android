package com.librechat.android.feature.settings.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.librechat.android.core.ui.components.ScreenTransitionWrapper
import com.librechat.android.feature.settings.screen.AccountSettingsScreen
import com.librechat.android.feature.settings.screen.ApiKeysScreen
import com.librechat.android.feature.settings.screen.ChatSettingsScreen
import com.librechat.android.feature.settings.screen.DataSettingsScreen
import com.librechat.android.feature.settings.screen.GeneralSettingsScreen
import com.librechat.android.feature.settings.screen.PresetManagerScreen
import com.librechat.android.feature.settings.screen.SharedLinksScreen
import com.librechat.android.feature.settings.screen.TabbedSettingsScreen
import com.librechat.android.feature.settings.viewmodel.SettingsViewModel

const val SETTINGS_TABBED_ROUTE = "settings"
const val SETTINGS_GENERAL_ROUTE = "settings/general"
const val SETTINGS_CHAT_ROUTE = "settings/chat"
const val SETTINGS_ACCOUNT_ROUTE = "settings/account"
const val SETTINGS_DATA_ROUTE = "settings/data"
const val SHARED_LINKS_ROUTE = "settings/shared-links"
const val PRESET_MANAGER_ROUTE = "settings/presets"
const val API_KEYS_ROUTE = "settings/api_keys"

fun NavGraphBuilder.settingsGraph(
    onLogout: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToArchived: () -> Unit = {},
    onNavigateToSharedLinks: () -> Unit = {},
    onNavigateBackFromSharedLinks: () -> Unit = {},
    onNavigateToPresets: () -> Unit = {},
    onNavigateBackFromPresets: () -> Unit = {},
    onNavigateToApiKeys: () -> Unit = {},
    onNavigateBackFromApiKeys: () -> Unit = {},
) {
    // Primary tabbed settings screen
    composable(SETTINGS_TABBED_ROUTE) {
        ScreenTransitionWrapper(transition) {
            TabbedSettingsScreen(
                onNavigateBack = onNavigateBack,
                onLogout = onLogout,
                onNavigateToArchived = onNavigateToArchived,
                onNavigateToSharedLinks = onNavigateToSharedLinks,
                onNavigateToPresets = onNavigateToPresets,
                onNavigateToApiKeys = onNavigateToApiKeys,
            )
        }
    }

    // Individual category routes kept for backward compatibility / deep linking
    composable(SETTINGS_GENERAL_ROUTE) {
        ScreenTransitionWrapper(transition) {
            GeneralSettingsScreen(
                onNavigateBack = onNavigateBack,
            )
        }
    }
    composable(SETTINGS_CHAT_ROUTE) {
        ScreenTransitionWrapper(transition) {
            ChatSettingsScreen(
                onNavigateBack = onNavigateBack,
                onNavigateToPresets = onNavigateToPresets,
            )
        }
    }
    composable(SETTINGS_ACCOUNT_ROUTE) {
        ScreenTransitionWrapper(transition) {
            AccountSettingsScreen(
                onLogout = onLogout,
                onNavigateBack = onNavigateBack,
                onNavigateToApiKeys = onNavigateToApiKeys,
            )
        }
    }
    composable(SETTINGS_DATA_ROUTE) {
        ScreenTransitionWrapper(transition) {
            DataSettingsScreen(
                onNavigateBack = onNavigateBack,
                onNavigateToArchived = onNavigateToArchived,
                onNavigateToSharedLinks = onNavigateToSharedLinks,
            )
        }
    }
    composable(SHARED_LINKS_ROUTE) {
        val viewModel: SettingsViewModel = hiltViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        // Load shared links when this screen is first shown
        androidx.compose.runtime.LaunchedEffect(Unit) {
            viewModel.loadSharedLinks()
        }

        ScreenTransitionWrapper(transition) {
            SharedLinksScreen(
                links = uiState.value.sharedLinks,
                isLoading = uiState.value.isSharedLinksLoading,
                hasNextPage = uiState.value.sharedLinksHasNextPage,
                serverUrl = uiState.value.serverUrl,
                onLoadMore = viewModel::loadMoreSharedLinks,
                onToggleVisibility = viewModel::toggleSharedLinkVisibility,
                onDelete = viewModel::deleteSharedLink,
                onNavigateBack = onNavigateBackFromSharedLinks,
            )
        }
    }
    composable(PRESET_MANAGER_ROUTE) {
        ScreenTransitionWrapper(transition) {
            PresetManagerScreen(
                onNavigateBack = onNavigateBackFromPresets,
            )
        }
    }
    composable(API_KEYS_ROUTE) {
        ScreenTransitionWrapper(transition) {
            ApiKeysScreen(
                onNavigateBack = onNavigateBackFromApiKeys,
            )
        }
    }
}
