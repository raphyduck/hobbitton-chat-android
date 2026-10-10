package com.garfiec.librechat.feature.tasks.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.garfiec.librechat.core.data.engine.EngineProfile
import com.garfiec.librechat.feature.tasks.MissionChatScreen
import com.garfiec.librechat.feature.tasks.MissionRunsScreen
import com.garfiec.librechat.feature.tasks.TasksScreen
import com.garfiec.librechat.feature.tasks.UsageScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface TasksRoute : NavKey

@Serializable data object TasksList : TasksRoute

/**
 * One mission session, opened as a live conversation. [title] names it in the chat's top bar.
 *
 * [sessionId] is null for a new task (02/10/2026): the conversation's own composer starts it, as a
 * new chat's does, and the navigation then replaces this entry with the real session's.
 *
 * [fromDrawer] decides the top bar's leading button, by the rule the rest of the app follows:
 * what is opened from the drawer carries the menu, like a chat does; what is opened from a list
 * carries the back arrow to that list. Defaulted, so a back stack saved before the field existed
 * still restores.
 */
@Serializable data class MissionChat(
    val sessionId: String? = null,
    val title: String = "",
    val fromDrawer: Boolean = false,
) : TasksRoute

/**
 * A chat (D-077): an engine session on the chat profile. [sessionId] is null for a new one, which
 * its first message creates; the navigation then replaces this entry with the real session's.
 *
 * The app's home: the drawer opens from it, and a fresh one is where « New chat » leads.
 */
@Serializable data class EngineChat(
    val sessionId: String? = null,
    val title: String = "",
) : TasksRoute

/** One scheduled mission's runs, opened from its card on the Tasks tab. */
@Serializable data class MissionRuns(val name: String) : TasksRoute

/** The week's spend and the providers' health, opened from Settings › Account. */
@Serializable data object TasksUsage : TasksRoute

/**
 * [onOpenDrawer] is null where the host has no drawer to open; the screens then fall back to what
 * they showed before it existed — nothing on Tasks, the back arrow on a mission.
 */
fun EntryProviderScope<NavKey>.tasksEntries(
    onOpenMissionChat: (sessionId: String, title: String) -> Unit,
    onBack: () -> Unit,
    onOpenMissionRuns: (name: String) -> Unit,
    onNewTask: () -> Unit,
    onTaskStart: (sessionId: String, title: String) -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
) {
    entry<TasksList> {
        TasksScreen(
            onOpenMissionChat = onOpenMissionChat,
            onOpenMissionRuns = onOpenMissionRuns,
            onNewTask = onNewTask,
            onOpenDrawer = onOpenDrawer,
        )
    }
    entry<MissionRuns> { key ->
        MissionRunsScreen(name = key.name, onOpenMissionChat = onOpenMissionChat, onBack = onBack)
    }
    entry<TasksUsage> { UsageScreen(onBack = onBack) }
    entry<MissionChat> { key ->
        MissionChatScreen(
            sessionId = key.sessionId,
            title = key.title,
            onBack = onBack,
            onOpenDrawer = onOpenDrawer.takeIf { key.fromDrawer },
            onChatStart = onTaskStart,
        )
    }
}

/**
 * The chat's entry (D-077), apart from [tasksEntries] because only the engine shell hosts it.
 * A chat is always a drawer destination: it carries the menu, never the back arrow.
 */
fun EntryProviderScope<NavKey>.engineChatEntries(
    onOpenDrawer: () -> Unit,
    onChatStart: (sessionId: String, title: String) -> Unit,
    onNewChat: () -> Unit,
    onBack: () -> Unit,
) {
    // One chat replaces another as the root: a cross-fade, not the slide of a pushed screen.
    entry<EngineChat>(metadata = NavDisplay.transitionSpec { crossfadeBetweenChats() }) { key ->
        MissionChatScreen(
            sessionId = key.sessionId,
            title = key.title,
            onBack = onBack,
            onOpenDrawer = onOpenDrawer,
            profile = EngineProfile.CHAT,
            onChatStart = onChatStart,
            onNewChat = onNewChat.takeIf { key.sessionId != null },
        )
    }
}

private fun crossfadeBetweenChats(): ContentTransform =
    fadeIn(tween(CHAT_FADE_MS)) togetherWith fadeOut(tween(CHAT_FADE_MS))

private const val CHAT_FADE_MS = 200

/**
 * Registered like every other feature's routes so a saved back stack survives process death. A
 * destination missing from here deserializes to nothing and the stack silently loses it.
 */
val tasksSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(TasksList::class)
        subclass(MissionChat::class)
        subclass(MissionRuns::class)
        subclass(TasksUsage::class)
        subclass(EngineChat::class)
    }
}
