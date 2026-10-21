package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.MissionReadingPosition
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.tasks.util.ChatPart
import com.garfiec.librechat.feature.tasks.util.ChatTurn
import com.garfiec.librechat.feature.tasks.util.MissionChatState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reopening a conversation lands where it was left (31/08/2026), even when the agent is talking
 * as it opens (21/10/2026), and leaving it remembers where the list stood, pause or no pause.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-rFR-w411dp-h891dp-port-420dpi")
class MissionTurnsPositionTest {

    @get:Rule
    val rule = createComposeRule()

    private val live = ChatTurn.Assistant("live", listOf(ChatPart.Text("lt", "Je regarde la boîte…")))
    private val history = (0 until 40).map { n ->
        ChatTurn.User("u$n", listOf(ChatPart.Text("t$n", "Message numéro $n")))
    }

    @Test
    fun `the saved position waits for the transcript, not for the feed's first turn`() {
        // The feed got there first: one live turn on screen, the history still loading.
        var chat by mutableStateOf(MissionChatState(turns = listOf(live), streaming = true))
        var historyLoaded by mutableStateOf(false)
        val written = mutableListOf<Pair<Int, Int>>()
        rule.setContent {
            LibreChatTheme {
                Box(Modifier.fillMaxWidth().height(600.dp)) {
                    MissionTurns(
                        chat = chat,
                        fontScale = 1f,
                        restoredPosition = MissionReadingPosition(index = 12, offset = 0),
                        positionKnown = true,
                        historyLoaded = historyLoaded,
                        onRememberPosition = { index, offset -> written += index to offset },
                    )
                }
            }
        }
        rule.waitForIdle()
        // Nothing restored, nothing written: a position taken on the feed's lone turn would be the tail.
        assertThat(written).isEmpty()

        rule.runOnIdle {
            chat = MissionChatState(turns = history + live, streaming = true)
            historyLoaded = true
        }
        rule.waitForIdle()

        rule.onNodeWithText("Message numéro 12").assertIsDisplayed()
        rule.onNodeWithText("Message numéro 0").assertDoesNotExist()
        rule.onNodeWithText("Je regarde la boîte…").assertDoesNotExist()
    }

    @Test
    fun `leaving the screen writes where the list stands`() {
        var shown by mutableStateOf(true)
        val written = mutableListOf<Pair<Int, Int>>()
        rule.setContent {
            LibreChatTheme {
                if (shown) {
                    Box(Modifier.fillMaxWidth().height(600.dp)) {
                        MissionTurns(
                            chat = MissionChatState(turns = history),
                            fontScale = 1f,
                            restoredPosition = null,
                            positionKnown = true,
                            historyLoaded = true,
                            onRememberPosition = { index, offset -> written += index to offset },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(25)
        // Gone before the settle pause has elapsed.
        rule.runOnIdle { shown = false }
        rule.waitForIdle()

        assertThat(written.last().first).isEqualTo(25)
    }
}
