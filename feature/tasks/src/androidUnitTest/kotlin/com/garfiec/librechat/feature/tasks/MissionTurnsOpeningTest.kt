package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.tasks.util.ChatPart
import com.garfiec.librechat.feature.tasks.util.ChatTurn
import com.garfiec.librechat.feature.tasks.util.MissionChatState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A conversation opens at the start of its last message (23/10/2026), even when the agent is
 * talking as it opens: the history, not the feed's first turn, decides where the end is.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-rFR-w411dp-h891dp-port-420dpi")
class MissionTurnsOpeningTest {

    @get:Rule
    val rule = createComposeRule()

    private val history = (0 until 40).map { n ->
        ChatTurn.User("u$n", listOf(ChatPart.Text("t$n", "Message numéro $n")))
    }

    /** A last message taller than the screen: its first line must be the one in view. */
    private val draft = ChatTurn.Assistant(
        "draft",
        listOf(
            ChatPart.Text(
                "d",
                (listOf("Début du brouillon") + (1..80).map { "Ligne $it du brouillon" } +
                    "Fin du brouillon").joinToString("\n\n"),
            ),
        ),
    )

    @Test
    fun `it opens at the start of the last message`() {
        rule.setContent {
            LibreChatTheme {
                Box(Modifier.fillMaxWidth().height(600.dp)) {
                    MissionTurns(
                        chat = MissionChatState(turns = history + draft),
                        fontScale = 1f,
                        historyLoaded = true,
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Début du brouillon", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Message numéro 0").assertDoesNotExist()
    }

    @Test
    fun `it waits for the transcript, not for the feed's first turn`() {
        val live = ChatTurn.Assistant("live", listOf(ChatPart.Text("lt", "Je regarde la boîte…")))
        var chat by mutableStateOf(MissionChatState(turns = listOf(live), streaming = true))
        var historyLoaded by mutableStateOf(false)
        rule.setContent {
            LibreChatTheme {
                Box(Modifier.fillMaxWidth().height(600.dp)) {
                    MissionTurns(chat = chat, fontScale = 1f, historyLoaded = historyLoaded)
                }
            }
        }
        rule.waitForIdle()

        rule.runOnIdle {
            chat = MissionChatState(turns = history + live, streaming = true)
            historyLoaded = true
        }
        rule.waitForIdle()

        rule.onNodeWithText("Je regarde la boîte", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Message numéro 0").assertDoesNotExist()
    }
}
