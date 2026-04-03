package com.librechat.android.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.librechat.android.feature.settings.screen.MemoriesScreen

const val MEMORIES_ROUTE = "settings/memories"

fun NavGraphBuilder.memoriesScreen(
    onNavigateBack: () -> Unit,
) {
    composable(MEMORIES_ROUTE) {
        MemoriesScreen(
            onNavigateBack = onNavigateBack,
        )
    }
}
