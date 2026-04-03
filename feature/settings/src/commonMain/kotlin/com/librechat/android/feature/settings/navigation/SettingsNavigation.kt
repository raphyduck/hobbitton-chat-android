package com.librechat.android.feature.settings.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.librechat.android.feature.settings.screen.AccountSettingsScreen
import com.librechat.android.feature.settings.screen.ApiKeysScreen
import com.librechat.android.feature.settings.screen.ChatSettingsScreen
import com.librechat.android.feature.settings.screen.DataSettingsScreen
import com.librechat.android.feature.settings.screen.GeneralSettingsScreen
import com.librechat.android.feature.settings.screen.PresetManagerScreen
import com.librechat.android.feature.settings.screen.SharedLinksScreen
import com.librechat.android.feature.settings.screen.TabbedSettingsScreen
import com.librechat.android.feature.settings.viewmodel.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

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
    composable(SETTINGS_TABBED_ROUTE) {
        TabbedSettingsScreen(
            onNavigateBack = onNavigateBack,
            onLogout = onLogout,
            onNavigateToArchived = onNavigateToArchived,
            onNavigateToSharedLinks = onNavigateToSharedLinks,
            onNavigateToPresets = onNavigateToPresets,
            onNavigateToApiKeys = onNavigateToApiKeys,
        )
    }

    composable(SETTINGS_GENERAL_ROUTE) {
        GeneralSettingsScreen(
            onNavigateBack = onNavigateBack,
        )
    }
    composable(SETTINGS_CHAT_ROUTE) {
        ChatSettingsScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToPresets = onNavigateToPresets,
        )
    }
    composable(SETTINGS_ACCOUNT_ROUTE) {
        AccountSettingsScreen(
            onLogout = onLogout,
            onNavigateBack = onNavigateBack,
            onNavigateToApiKeys = onNavigateToApiKeys,
        )
    }
    composable(SETTINGS_DATA_ROUTE) {
        DataSettingsScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToArchived = onNavigateToArchived,
            onNavigateToSharedLinks = onNavigateToSharedLinks,
        )
    }
    composable(SHARED_LINKS_ROUTE) {
        val viewModel: SettingsViewModel = koinViewModel()
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.loadSharedLinks()
        }

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
    composable(PRESET_MANAGER_ROUTE) {
        PresetManagerScreen(
            onNavigateBack = onNavigateBackFromPresets,
        )
    }
    composable(API_KEYS_ROUTE) {
        ApiKeysScreen(
            onNavigateBack = onNavigateBackFromApiKeys,
        )
    }
}
