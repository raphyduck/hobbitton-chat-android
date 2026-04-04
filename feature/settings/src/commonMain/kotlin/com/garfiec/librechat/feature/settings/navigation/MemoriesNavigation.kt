package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.garfiec.librechat.feature.settings.screen.MemoriesScreen
import kotlinx.serialization.Serializable

@Serializable data object Memories : SettingsRoute

fun NavGraphBuilder.memoriesScreen(
    onNavigateBack: () -> Unit,
) {
    composable<Memories> {
        MemoriesScreen(
            onNavigateBack = onNavigateBack,
        )
    }
}
