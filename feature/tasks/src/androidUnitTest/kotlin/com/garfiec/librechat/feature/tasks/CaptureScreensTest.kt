package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.engine.ConnectorOption
import com.garfiec.librechat.core.data.engine.EngineProfile
import com.garfiec.librechat.core.data.engine.Mission
import com.garfiec.librechat.core.model.engine.EngineQuestionInfo
import com.garfiec.librechat.core.model.engine.EngineQuestionOption
import com.garfiec.librechat.core.model.engine.EngineQuestionRequest
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.engine.MissionState
import com.garfiec.librechat.core.model.scheduler.MissionRun
import com.garfiec.librechat.core.model.scheduler.ScheduledMission
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.tasks.util.ChatPart
import com.garfiec.librechat.feature.tasks.util.ChatTurn
import com.garfiec.librechat.feature.tasks.util.MissionChatState
import com.garfiec.librechat.feature.tasks.util.ToolArgument
import com.garfiec.librechat.feature.tasks.util.ToolState
import com.garfiec.librechat.feature.tasks.util.mergedAssistantRuns
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the conversation, the Tasks tab and the question form the way they ship, and writes them
 * as PNGs under `build/captures/` (`./gradlew :feature:tasks:recordRoborazziDebug`). The screens
 * take a ViewModel, so what is composed here is their body and their bars, from the same internal
 * pieces, over fixed state: the point is to see them without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-rFR-w411dp-h891dp-port-420dpi")
class CaptureScreensTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun chatLight() = capture("chat-light", dark = false) { ChatScreen(SETTLED) }

    @Test
    fun chatDark() = capture("chat-dark", dark = true) { ChatScreen(SETTLED) }

    @Test
    fun chatStreaming() = capture("chat-streaming", dark = false) { ChatScreen(STREAMING) }

    @Test
    fun chatEmpty() = capture("chat-empty", dark = false) { ChatScreen(EMPTY) }

    @Test
    fun chatQuestion() = capture("chat-question", dark = false) { ChatScreen(QUESTION) }

    @Test
    fun tasksLight() = capture("tasks-light", dark = false) { TasksList() }

    @Test
    fun tasksDark() = capture("tasks-dark", dark = true) { TasksList() }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.setContent {
            LibreChatTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) { content() }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/captures/$name.png")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(state: MissionChatUiState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversation") },
                navigationIcon = { IconButton(onClick = {}) { Icon(Icons.Default.Menu, null) } },
                actions = { IconButton(onClick = {}) { Icon(Icons.Outlined.Edit, null) } },
            )
        },
        bottomBar = {
            val question = state.pendingQuestion
            if (question != null) {
                MissionQuestionForm(
                    request = question,
                    draft = state.questionDraftFor(question),
                    sending = false,
                    error = null,
                    onPick = { _, _ -> },
                    onType = { _, _ -> },
                    onSend = {},
                    onDismiss = {},
                )
            } else {
                MissionChatInput(
                    state = state,
                    onInput = {},
                    onSend = {},
                    onStop = {},
                    onDismissError = {},
                    onToggleConnector = {},
                    onSelectModel = {},
                    onRetryCatalogue = {},
                    onAddAttachments = {},
                    onRemoveAttachment = {},
                    onTranscribeAudio = { _, _, _ -> },
                    onAttachAudio = { _, _, _ -> },
                    onRemoveAudioNote = {},
                    onDismissTranscriptionError = {},
                )
            }
        },
    ) { padding ->
        val turns = state.chat.turns.mergedAssistantRuns()
        if (turns.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyConversation(
                    profile = EngineProfile.CHAT,
                    isNew = true,
                    greetingName = "Raphaël",
                    onPickPrompt = {},
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(turns.size, key = { turns[it].key }) { index ->
                    val turn = turns[index]
                    val live = state.chat.streaming && index == turns.lastIndex
                    SelectionContainer {
                        when (turn) {
                            is ChatTurn.User -> UserBubble(turn, 1f)
                            is ChatTurn.Assistant -> AssistantTurn(turn, streaming = live, fontScale = 1f)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksList() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tâches") },
                navigationIcon = { IconButton(onClick = {}) { Icon(Icons.Default.Menu, null) } },
            )
        },
        floatingActionButton = { NewTaskButton(onClick = {}) },
    ) { padding ->
        TasksListBody(
            scheduled = SCHEDULED,
            missions = MISSIONS,
            scheduledShown = true,
            onToggleSchedule = {},
            failureTitle = null,
            actions = NO_ACTIONS,
            modifier = Modifier.padding(padding),
        )
    }
}

// The clock of the capture: the ages on the rows are read against the real clock.
private val NOW = kotlin.time.Clock.System.now().toEpochMilliseconds()

private val NO_ACTIONS = TasksListActions(
    onOpenMission = {},
    onStopMission = {},
    onOpenRuns = {},
    onRunScheduled = {},
    onSetScheduledEnabled = { _, _ -> },
    onReschedule = { _, _, _ -> },
    onDeleteScheduled = {},
)

private val SCHEDULED = listOf(
    ScheduledMission(
        name = "point-mails-matin",
        profile = "mission",
        enabled = true,
        cron = "0 7 * * 1-5",
        timeZone = "Europe/Paris",
        nextRun = "2026-10-13T07:00",
        connectors = listOf("imap", "cerveau"),
        declaredTools = 14,
        lastRun = MissionRun(startedAt = "2026-10-10T07:00", tokens = 48_210, succeeded = true),
    ),
    ScheduledMission(
        name = "veille-tarifs-scpi",
        profile = "mission",
        enabled = true,
        cron = "0 6 * * 1",
        timeZone = "Europe/Paris",
        nextRun = "2026-10-13T06:00",
        declaredTools = 9,
        lastRun = MissionRun(startedAt = "2026-10-06T06:00", succeeded = false, stopReason = "BUDGET DÉPASSÉ (120 000 jetons)"),
    ),
    ScheduledMission(
        name = "sauvegarde-nas",
        profile = "mission",
        enabled = false,
        cron = "30 2 * * *",
        timeZone = "Europe/Paris",
        declaredTools = 6,
    ),
)

private val MISSIONS = listOf(
    Mission("s1", "Vente Audi Q5 : relance des acheteurs", MissionState.Running("running"), NOW - 4 * 60_000),
    Mission("s2", "point-mails-matin", MissionState.Succeeded(48_210), NOW - 9 * 3_600_000),
    Mission("s3", "veille-tarifs-scpi", MissionState.Failed("Request timeout after 120 s on connector pennylane", 3_100), NOW - 26 * 3_600_000),
    Mission("s4", "Devis assurance : comparer trois offres", MissionState.Succeeded(12_480), NOW - 2 * 24 * 3_600_000),
)


private val MODELS = listOf(
    EngineSelectableModel("hobbitton-chat", "claude-sonnet-5-5", "Claude Sonnet 5.5"),
    EngineSelectableModel("hobbitton-chat", "claude-opus-5-5", "Claude Opus 5.5"),
)
private val CONNECTORS = listOf(
    ConnectorOption("imap", 12, tickedByDefault = true),
    ConnectorOption("gcal", 8, tickedByDefault = true),
    ConnectorOption("nas", 6),
)

private val BASE = MissionChatUiState(
    loadingHistory = false,
    positionKnown = true,
    profile = EngineProfile.CHAT,
    models = MODELS,
    model = MODELS[0],
    connectors = CONNECTORS,
    enabledConnectors = setOf("imap", "gcal", "nas"),
)

private val FIRST_EXCHANGE = listOf(
    ChatTurn.User("u1", listOf(ChatPart.Text("u1t", "Résume mes e-mails non lus de ce matin"))),
    ChatTurn.Assistant(
        "a1",
        listOf(
            ChatPart.Reasoning("a1r", "Je cherche les messages non lus depuis minuit, puis je lis les trois qui demandent une réponse."),
            ChatPart.Tool("a1t1", "imap_search_emails", ToolState.OK, listOf(ToolArgument("query", "UNSEEN SINCE 10-Oct-2026")), "3 messages"),
            ChatPart.Tool("a1t2", "imap_get_email", ToolState.OK, listOf(ToolArgument("uid", "48213")), "Objet : Rendez-vous jeudi…"),
            ChatPart.Text(
                "a1p",
                "Trois e-mails non lus ce matin :\n\n" +
                    "- **Marc Delorme** propose de décaler le rendez-vous à **jeudi 14 h**. Il attend une confirmation.\n" +
                    "- **Qonto** : le relevé de septembre est disponible.\n" +
                    "- **Leboncoin** : un acheteur demande si le Q5 a un carnet d'entretien complet.\n\n" +
                    "Voulez-vous que je réponde à Marc ?",
            ),
        ),
    ),
)

private val SETTLED = BASE.copy(
    chat = MissionChatState(
        turns = FIRST_EXCHANGE + listOf(
            ChatTurn.User("u2", listOf(ChatPart.Text("u2t", "Oui, dis-lui que jeudi 14 h me va."))),
            ChatTurn.Assistant(
                "a2",
                listOf(
                    ChatPart.Tool("a2t1", "imap_reply_to_email", ToolState.OK, listOf(ToolArgument("uid", "48213")), "envoyé"),
                    ChatPart.Text("a2p", "C'est fait : j'ai répondu à Marc que jeudi 14 h vous convient."),
                ),
            ),
        ),
    ),
)

private val STREAMING = BASE.copy(
    chat = MissionChatState(
        streaming = true,
        turns = FIRST_EXCHANGE + listOf(
            ChatTurn.User("u2", listOf(ChatPart.Text("u2t", "Oui, dis-lui que jeudi 14 h me va."))),
            ChatTurn.Assistant(
                "a2",
                listOf(
                    ChatPart.Tool("a2t1", "imap_reply_to_email", ToolState.RUNNING, listOf(ToolArgument("uid", "48213"))),
                    ChatPart.Text("a2p", "J'envoie la réponse à Marc"),
                ),
            ),
        ),
    ),
)

private val EMPTY = BASE

private val QUESTION = BASE.copy(
    chat = MissionChatState(
        streaming = true,
        turns = FIRST_EXCHANGE,
        questions = listOf(
            EngineQuestionRequest(
                id = "q1",
                sessionId = "s",
                questions = listOf(
                    EngineQuestionInfo(
                        header = "Réponse à Marc",
                        question = "Sur quel ton répondre à Marc ?",
                        options = listOf(
                            EngineQuestionOption("Amical", "Tutoiement, comme d'habitude"),
                            EngineQuestionOption("Neutre", "Vouvoiement, bref"),
                        ),
                    ),
                ),
            ),
        ),
    ),
)
