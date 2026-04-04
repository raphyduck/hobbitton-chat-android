package com.garfiec.librechat.feature.files.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.files.screen.FilesScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface FilesRoute : NavKey

@Serializable data object Files : FilesRoute

fun EntryProviderScope<NavKey>.filesEntries(
    onBack: (() -> Unit)? = null,
) {
    entry<Files> {
        FilesScreen(onBack = onBack)
    }
}

val filesSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(Files::class, Files.serializer())
    }
}
