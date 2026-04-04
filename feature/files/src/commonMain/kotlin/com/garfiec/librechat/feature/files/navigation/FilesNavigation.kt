package com.garfiec.librechat.feature.files.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.garfiec.librechat.core.ui.components.ScreenTransitionWrapper
import com.garfiec.librechat.feature.files.screen.FilesScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface FilesRoute

@Serializable data object Files : FilesRoute

fun NavGraphBuilder.filesGraph(
    onBack: (() -> Unit)? = null,
) {
    composable<Files> {
        ScreenTransitionWrapper(transition) {
            FilesScreen(onBack = onBack)
        }
    }
}

val filesSerializersModule = SerializersModule {
    polymorphic(FilesRoute::class) {
        subclass(Files::class, Files.serializer())
    }
}
