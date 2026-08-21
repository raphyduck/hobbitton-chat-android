package com.garfiec.librechat.feature.tasks.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.tasks.TasksScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface TasksRoute : NavKey

@Serializable data object TasksList : TasksRoute

fun EntryProviderScope<NavKey>.tasksEntries() {
    entry<TasksList> { TasksScreen() }
}

/**
 * Registered like every other feature's routes so a saved back stack survives process death. A
 * destination missing from here deserializes to nothing and the stack silently loses it.
 */
val tasksSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(TasksList::class)
    }
}
