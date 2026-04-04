package com.garfiec.librechat.feature.settings.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.garfiec.librechat.feature.settings.screen.AccountSettingsScreen
import com.garfiec.librechat.feature.settings.screen.ApiKeysScreen
import com.garfiec.librechat.feature.settings.screen.ChatSettingsScreen
import com.garfiec.librechat.feature.settings.screen.DataSettingsScreen
import com.garfiec.librechat.feature.settings.screen.GeneralSettingsScreen
import com.garfiec.librechat.feature.settings.screen.PresetManagerScreen
import com.garfiec.librechat.feature.settings.screen.SharedLinksScreen
import com.garfiec.librechat.feature.settings.screen.TabbedSettingsScreen
import com.garfiec.librechat.feature.settings.viewmodel.SettingsViewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.koin.compose.viewmodel.koinViewModel

@Serializable sealed interface SettingsRoute

@Serializable data object SettingsTabbed : SettingsRoute
@Serializable data object SettingsGeneral : SettingsRoute
@Serializable data object SettingsChat : SettingsRoute
@Serializable data object SettingsAccount : SettingsRoute
@Serializable data object SettingsData : SettingsRoute
@Serializable data object SharedLinks : SettingsRoute
@Serializable data object PresetManager : SettingsRoute
@Serializable data object ApiKeys : SettingsRoute

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
    composable<SettingsTabbed> {
        TabbedSettingsScreen(
            onNavigateBack = onNavigateBack,
            onLogout = onLogout,
            onNavigateToArchived = onNavigateToArchived,
            onNavigateToSharedLinks = onNavigateToSharedLinks,
            onNavigateToPresets = onNavigateToPresets,
            onNavigateToApiKeys = onNavigateToApiKeys,
        )
    }

    composable<SettingsGeneral> {
        GeneralSettingsScreen(
            onNavigateBack = onNavigateBack,
        )
    }
    composable<SettingsChat> {
        ChatSettingsScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToPresets = onNavigateToPresets,
        )
    }
    composable<SettingsAccount> {
        AccountSettingsScreen(
            onLogout = onLogout,
            onNavigateBack = onNavigateBack,
            onNavigateToApiKeys = onNavigateToApiKeys,
        )
    }
    composable<SettingsData> {
        DataSettingsScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToArchived = onNavigateToArchived,
            onNavigateToSharedLinks = onNavigateToSharedLinks,
        )
    }
    composable<SharedLinks> {
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
    composable<PresetManager> {
        PresetManagerScreen(
            onNavigateBack = onNavigateBackFromPresets,
        )
    }
    composable<ApiKeys> {
        ApiKeysScreen(
            onNavigateBack = onNavigateBackFromApiKeys,
        )
    }
}

val settingsSerializersModule = SerializersModule {
    polymorphic(SettingsRoute::class) {
        subclass(SettingsTabbed::class, SettingsTabbed.serializer())
        subclass(SettingsGeneral::class, SettingsGeneral.serializer())
        subclass(SettingsChat::class, SettingsChat.serializer())
        subclass(SettingsAccount::class, SettingsAccount.serializer())
        subclass(SettingsData::class, SettingsData.serializer())
        subclass(SharedLinks::class, SharedLinks.serializer())
        subclass(PresetManager::class, PresetManager.serializer())
        subclass(ApiKeys::class, ApiKeys.serializer())
        subclass(Memories::class, Memories.serializer())
        subclass(McpServers::class, McpServers.serializer())
    }
}
