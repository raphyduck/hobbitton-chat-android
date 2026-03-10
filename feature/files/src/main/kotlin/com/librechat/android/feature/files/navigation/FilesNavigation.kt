package com.librechat.android.feature.files.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.librechat.android.core.ui.components.ScreenTransitionWrapper
import com.librechat.android.feature.files.screen.FilesScreen

const val FILES_ROUTE = "files"

fun NavGraphBuilder.filesGraph() {
    composable(FILES_ROUTE) {
        ScreenTransitionWrapper(transition) {
            FilesScreen()
        }
    }
}
