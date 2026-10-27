package com.garfiec.librechat.shared.engine

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import com.garfiec.librechat.feature.tasks.navigation.EngineChat
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

/** The global instructions' editor, opened from the settings (D-077). */
@Serializable data object EngineInstructions : NavKey

/** The conversations' spending ceilings, opened from the settings (12/10/2026). */
@Serializable data object EngineBudget : NavKey

/** Every conversation, the scheduler's runs included, unread first (16/10/2026). */
@Serializable data object EngineConversations : NavKey

/** The voice mode (27/10/2026): a room with the voice agent, opened from the drawer. */
@Serializable data object EngineVoice : NavKey

/**
 * Every route the engine shell can hold (the conversation, Conversations, Programmées and the settings), and
 * nothing of LibreChat's. A route missing from here deserializes to nothing and a restored stack loses it.
 */
val engineShellSavedStateConfig = SavedStateConfiguration {
    serializersModule = tasksSerializersModule + SerializersModule {
        polymorphic(NavKey::class) {
            subclass(EngineAppSettings::class)
            subclass(EngineInstructions::class)
            subclass(EngineBudget::class)
            subclass(EngineConversations::class)
            subclass(EngineVoice::class)
        }
    }
}

/**
 * The engine shell's back stack mutations (D-077), in one place so they can be tested.
 *
 * The conversation is the root: every drawer destination that is a conversation replaces it,
 * Programmées and the settings are pushed over it, and backing out of either lands on it again.
 * Backing out of a session at the root lands on the Conversations page, which takes its place
 * (Raphaël's rule, 20/10/2026: back used to close the app); backing out of that page closes it.
 */
class EngineNavigator(val backStack: NavBackStack<NavKey>) {

    val currentRoute: NavKey? get() = backStack.lastOrNull()

    /** Pops one entry; at the floor, a session gives way to the Conversations page. */
    fun goBack() {
        if (backStack.size > 1) backStack.removeLastOrNull() else backToConversations()
    }

    /** A session alone on the stack: back shows the Conversations page rather than closing the app. */
    val backLeadsToConversations: Boolean
        get() = backStack.size == 1 && (backStack.lastOrNull() as? EngineChat)?.sessionId != null

    /** The Conversations page reached that way: the floor, so it offers the menu, not a back arrow. */
    val conversationsAtFloor: Boolean
        get() = backStack.size == 1 && backStack.lastOrNull() == EngineConversations

    /** The page slipped under the session, then the session popped: it arrives as a back, not a push. */
    fun backToConversations() {
        if (!backLeadsToConversations) return
        backStack.add(0, EngineConversations)
        backStack.removeLastOrNull()
    }

    /** A blank chat as the root. A no-op when one is already all there is. */
    fun newChat() = openAsRoot(EngineChat())

    /** A conversation from the drawer or a notification, as the root: any session (D-082). */
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

    /** Conversations: pushed over the conversation, unwound to when already on the stack. */
    fun openConversations() = pushOrUnwind(EngineConversations)

    /** Programmées: pushed over the conversation, unwound to when already on the stack. */
    fun openTasks() = pushOrUnwind(TasksList)

    fun openSettings() {
        if (currentRoute != EngineAppSettings) backStack.add(EngineAppSettings)
    }

    /** The voice mode, pushed over whatever is shown. */
    fun openVoice() {
        if (currentRoute != EngineVoice) backStack.add(EngineVoice)
    }

    /** The instructions' editor, pushed over the settings. */
    fun openInstructions() {
        if (currentRoute != EngineInstructions) backStack.add(EngineInstructions)
    }

    /** The ceilings' editor, pushed over the settings. */
    fun openBudget() {
        if (currentRoute != EngineBudget) backStack.add(EngineBudget)
    }

    fun openUsage() {
        if (currentRoute != TasksUsage) backStack.add(TasksUsage)
    }

    private fun pushOrUnwind(route: NavKey) {
        if (route !in backStack) {
            backStack.add(route)
            return
        }
        while (backStack.lastOrNull() != route) backStack.removeLastOrNull()
    }

    private fun openAsRoot(route: NavKey) {
        while (backStack.size > 1) backStack.removeLastOrNull()
        if (backStack.lastOrNull() != route) {
            backStack.removeLastOrNull()
            backStack.add(route)
        }
    }
}
