package com.garfiec.librechat.feature.files.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.garfiec.librechat.core.ui.components.ScreenTransitionWrapper
import com.garfiec.librechat.feature.files.screen.FilesScreen

const val FILES_ROUTE = "files"

fun NavGraphBuilder.filesGraph(
    onBack: (() -> Unit)? = null,
) {
    composable(FILES_ROUTE) {
        ScreenTransitionWrapper(transition) {
            FilesScreen(onBack = onBack)
        }
    }
}
