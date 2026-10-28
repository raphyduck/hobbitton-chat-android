package com.garfiec.librechat.shared.engine

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.garfiec.librechat.feature.tasks.navigation.EngineChat
import java.util.IdentityHashMap
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shell's view model stores (28/10/2026): a screen never shows a view model that was cleared,
 * and a view model outlives its entry only while the entry is still drawn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntryViewModelStoresTest {

    @get:Rule
    val rule = createComposeRule()

    private class Probe(val label: String, val entryKey: String) : ViewModel() {
        var cleared = false
        override fun onCleared() {
            cleared = true
        }
    }

    private val created = mutableListOf<Probe>()

    /** The view model each pushed entry (by identity) was last drawn with. */
    private val drawnWith = IdentityHashMap<NavKey, Probe>()

    /** The view models drawn right now: a drawn view model must never be cleared. */
    private val drawn = mutableSetOf<Probe>()

    private fun shell(vararg start: NavKey): EngineNavigator {
        val stack = NavBackStack(*start)
        val navigator = EngineNavigator(stack)
        rule.setContent {
            NavDisplay(
                backStack = stack,
                onBack = { navigator.goBack() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberEntryViewModelStoreDecorator(stack, ::shellContentKey),
                ),
                entryProvider = entryProvider {
                    entry<EngineChat>(clazzContentKey = { it.entryKey }) { key -> Conversation(key) }
                    entry<EngineConversations> { Text("conversations") }
                    entry<EngineAppSettings> { Text("settings") }
                    entry<EngineVoice> { Text("voice") }
                },
            )
        }
        rule.waitForIdle()
        return navigator
    }

    @Composable
    private fun Conversation(key: EngineChat) {
        val probe = viewModel { Probe("${key.sessionId}/${key.title}", key.entryKey).also { created += it } }
        SideEffect { drawnWith[key] = probe }
        DisposableEffect(probe) {
            drawn += probe
            onDispose { drawn -= probe }
        }
        Text(probe.label)
    }

    private fun shownOf(navigator: EngineNavigator): Probe? = navigator.backStack.lastOrNull()?.let { drawnWith[it] }

    @Test
    fun `a conversation under a pushed screen keeps its view model`() {
        val navigator = shell(EngineChat("ses_1", "Bonjour"))
        val first = shownOf(navigator)

        rule.runOnIdle { navigator.openSettings() }
        rule.waitForIdle()
        rule.runOnIdle { navigator.goBack() }
        rule.waitForIdle()

        assertTrue(first === shownOf(navigator))
        assertFalse(first!!.cleared)
    }

    @Test
    fun `a conversation left is cleared once it is no longer drawn`() {
        val navigator = shell(EngineChat("ses_1", "Bonjour"))
        val first = shownOf(navigator)!!

        rule.runOnIdle { navigator.openChat("ses_2", "Autre") }
        rule.waitForIdle()

        assertTrue(first.cleared)
        assertFalse(shownOf(navigator)!!.cleared)
    }

    @Test
    fun `a conversation reopened while it fades out gets a live view model`() {
        val navigator = shell(EngineChat("ses_1", "Bonjour"))
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { navigator.openChat("ses_2", "Autre") }
        rule.mainClock.advanceTimeBy(FRAME_MS * 3)
        rule.runOnIdle { navigator.openChat("ses_1", "Bonjour") }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertTrue(drawn.any { it.label == "ses_1/Bonjour" })
        assertEquals(emptyList<String>(), drawn.filter { it.cleared }.map { it.label })
    }

    /**
     * Thousands of gestures as the drawer, the pages and the back gesture make them, at random
     * paces, some within a transition: the screen on top never shows a cleared view model, and
     * none outlives its entry.
     */
    @Test
    fun `no gesture leaves a cleared view model on screen, none leaks`() {
        val navigator = shell(EngineChat("s", "t"))
        val random = Random(SEED)
        val sessions = listOf("s", "u", "v")
        val titles = listOf("t", "", "t2")
        repeat(ROUNDS) { round ->
            val gestures = mutableListOf<String>()
            rule.mainClock.autoAdvance = false
            repeat(1 + random.nextInt(4)) {
                val session = sessions[random.nextInt(sessions.size)]
                val title = titles[random.nextInt(titles.size)]
                val gesture = random.nextInt(GESTURES)
                rule.runOnIdle {
                    gestures += when (gesture) {
                        0, 1, 2 -> "open($session,$title)".also { navigator.openChat(session, title) }
                        3, 4 -> "back".also { navigator.goBack() }
                        5 -> "back to the list".also { navigator.backToConversations() }
                        6 -> "voice".also { navigator.openVoice() }
                        7 -> "conversations".also { navigator.openConversations() }
                        8 -> "new".also { navigator.newChat() }
                        9 -> "started($session,$title)".also { navigator.chatStarted(session, title) }
                        else -> "settings".also { navigator.openSettings() }
                    }
                }
                val pause = PAUSES_MS[random.nextInt(PAUSES_MS.size)]
                gestures += "+$pause ms"
                rule.mainClock.advanceTimeBy(pause)
            }
            rule.mainClock.autoAdvance = true
            rule.waitForIdle()
            val dead = drawn.filter { it.cleared }.map { it.label }
            assertEquals("round $round $gestures: a drawn view model was cleared", emptyList<String>(), dead)
        }
        val onStack = navigator.backStack.map(::shellContentKey).toSet()
        val leaked = created.filter { !it.cleared && it !in drawn && it.entryKey !in onStack }
        assertEquals("view models alive without their entry", emptyList<String>(), leaked.map { it.label })
    }

    private companion object {
        const val SEED = 7L
        const val ROUNDS = 1500
        const val GESTURES = 11
        const val FRAME_MS = 16L
        val PAUSES_MS = listOf(0L, 16L, 50L, 120L, 250L, 400L)
    }
}
