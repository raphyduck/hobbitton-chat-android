package com.garfiec.librechat.shared.engine

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import com.garfiec.librechat.feature.tasks.navigation.EngineChat
import com.garfiec.librechat.feature.tasks.navigation.MissionChat
import com.garfiec.librechat.feature.tasks.navigation.MissionRuns
import com.garfiec.librechat.feature.tasks.navigation.TasksList
import com.garfiec.librechat.feature.tasks.navigation.TasksUsage
import com.garfiec.librechat.feature.tasks.navigation.tasksSerializersModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** The engine shell's settings: appearance, the platform's addresses, usage, sign-out (D-077). */
@Serializable data object EngineAppSettings : NavKey

/**
 * Every route the engine shell can hold — the chat, the tasks and the settings, and nothing of
 * LibreChat's. A route missing from here deserializes to nothing and a restored stack loses it.
 */
val engineShellSavedStateConfig = SavedStateConfiguration {
    serializersModule = tasksSerializersModule + SerializersModule {
        polymorphic(NavKey::class) {
            subclass(EngineAppSettings::class)
        }
    }
}

/**
 * The engine shell's back stack mutations (D-077), in one place so they can be tested.
 *
 * The chat is the root: every drawer destination that is a conversation replaces it, the Tasks tab
 * and the settings are pushed over it, and backing out of either lands on the chat again.
 */
class EngineNavigator(val backStack: NavBackStack<NavKey>) {

    val currentRoute: NavKey? get() = backStack.lastOrNull()

    /** Pops one entry, never the last one: the chat is the floor. */
    fun goBack() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    /** A blank chat as the root. A no-op when one is already all there is. */
    fun newChat() = openAsRoot(EngineChat())

    /** A chat from the drawer, as the root. */
    fun openChat(sessionId: String, title: String) = openAsRoot(EngineChat(sessionId = sessionId, title = title))

    /**
     * A new chat just came into existence on the engine: the blank entry that created it is
     * replaced by the real session's, in place, so backing out never returns to an empty composer
     * that no longer means anything.
     */
    fun chatStarted(sessionId: String, title: String) {
        val index = backStack.indexOfLast { it is EngineChat && it.sessionId == null }
        val started = EngineChat(sessionId = sessionId, title = title)
        if (index >= 0) backStack[index] = started else openAsRoot(started)
    }

    /** The Tasks tab: pushed over the chat, unwound to when already on the stack. */
    fun openTasks() {
        if (TasksList !in backStack) {
            backStack.add(TasksList)
            return
        }
        while (backStack.lastOrNull() != TasksList) backStack.removeLastOrNull()
    }

    fun openSettings() {
        if (currentRoute != EngineAppSettings) backStack.add(EngineAppSettings)
    }

    fun openMission(sessionId: String, title: String) {
        backStack.add(MissionChat(sessionId = sessionId, title = title))
    }

    fun openMissionRuns(name: String) {
        backStack.add(MissionRuns(name))
    }

    fun openUsage() {
        if (currentRoute != TasksUsage) backStack.add(TasksUsage)
    }

    private fun openAsRoot(route: NavKey) {
        while (backStack.size > 1) backStack.removeLastOrNull()
        if (backStack.lastOrNull() != route) {
            backStack.removeLastOrNull()
            backStack.add(route)
        }
    }
}
