package com.garfiec.librechat.feature.tasks.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.garfiec.librechat.feature.tasks.MissionChatScreen
import com.garfiec.librechat.feature.tasks.TasksScreen
import com.garfiec.librechat.feature.tasks.UsageScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.random.Random

@Serializable sealed interface TasksRoute : NavKey

@Serializable data object TasksList : TasksRoute

/**
 * A conversation: any engine session since D-082 (12/10/2026), whether a chat, a task of an earlier
 * version, a scheduled mission's run. [sessionId] is null for a new one, which its first message
 * creates; the navigation then replaces this entry with the real session's.
 *
 * The app's home: the drawer opens from it, and a fresh one is where « New chat » leads. The routes
 * a task and a mission's runs had (`MissionChat`, `MissionRuns`) went with the distinction.
 */
@Serializable data class EngineChat(
    val sessionId: String? = null,
    val title: String = "",
) : TasksRoute {
    /**
     * This push of the conversation, out of `equals` (28/10/2026, Raphaël: « ça recommence », a
     * conversation grey for good). Navigation 3 keys an entry's view models by its content key and
     * clears them when an entry leaves, sometimes a transition late: by then an equal entry pushed
     * again (a blank chat after « new », a conversation reopened) had put its view model in the same
     * store, and the late clear killed it while it was on screen, its opening cancelled before the
     * first read. Each push has a store of its own; equality still says « the same place », which
     * is what the navigator compares. Saved with the back stack, so a restored entry keeps it.
     */
    val instance: Long = Random.nextLong()

    /** The content key: what the entry's view models and saved state are kept under. */
    val entryKey: String get() = "$sessionId|$title|$instance"
}

/** The week's spend and the providers' health, opened from Settings › Account. */
@Serializable data object TasksUsage : TasksRoute

/**
 * Programmées and the usage screen. [onOpenDrawer] is null where the host has no drawer to open;
 * Programmées then shows no leading button.
 */
fun EntryProviderScope<NavKey>.tasksEntries(
    onBack: () -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
) {
    entry<TasksList> { TasksScreen(onOpenDrawer = onOpenDrawer) }
    entry<TasksUsage> { UsageScreen(onBack = onBack) }
}

/**
 * The conversation's entry, apart from [tasksEntries] because only the engine shell hosts it.
 * A conversation is always a drawer destination: it carries the menu, never the back arrow.
 */
fun EntryProviderScope<NavKey>.engineChatEntries(
    onOpenDrawer: () -> Unit,
    onChatStart: (sessionId: String, title: String) -> Unit,
    onNewChat: () -> Unit,
    onBack: () -> Unit,
) {
    // One chat replaces another as the root: a cross-fade, not the slide of a pushed screen.
    entry<EngineChat>(
        clazzContentKey = { it.entryKey },
        metadata = NavDisplay.transitionSpec { crossfadeBetweenChats() },
    ) { key ->
        MissionChatScreen(
            sessionId = key.sessionId,
            title = key.title,
            onBack = onBack,
            onOpenDrawer = onOpenDrawer,
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
        subclass(TasksUsage::class)
        subclass(EngineChat::class)
    }
}
