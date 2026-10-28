package com.garfiec.librechat.shared.engine

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.garfiec.librechat.core.data.engine.EngineChatSummary
import com.garfiec.librechat.core.data.engine.EngineSessionKind
import com.garfiec.librechat.core.data.engine.inboxOrder
import com.garfiec.librechat.core.data.lock.LockState
import com.garfiec.librechat.core.model.scheduler.ChatBudget
import com.garfiec.librechat.core.network.engine.EngineAccess
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.shared.engine.lock.AppLockUiState
import com.garfiec.librechat.shared.engine.lock.CodeEntry
import com.garfiec.librechat.shared.engine.lock.LockSurface
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The drawer, the settings and the spending ceilings, rendered to `build/captures/` (`-Pcaptures`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-rFR-w411dp-h891dp-port-420dpi")
class CaptureShellTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun drawerLight() = capture("drawer-light", dark = false) { Drawer() }

    @Test
    fun drawerDark() = capture("drawer-dark", dark = true) { Drawer() }

    @Test
    fun conversationsLight() = capture("conversations-light", dark = false) { Conversations() }

    @Test
    fun conversationsDark() = capture("conversations-dark", dark = true) { Conversations() }

    @Test
    fun settingsLight() = capture("settings-light", dark = false) { Settings() }

    @Test
    fun settingsDark() = capture("settings-dark", dark = true) { Settings() }

    @Test
    fun budgetLight() = capture("budget-light", dark = false) { Budget() }

    @Test
    fun budgetDark() = capture("budget-dark", dark = true) { Budget() }

    @Test
    fun budgetRefused() = capture("budget-refused", dark = false) {
        Budget(day = "50", problem = BudgetProblem.DAY_OVER_WEEK)
    }

    @Test
    fun lockLight() = capture("lock-light", dark = false) { Lock(error = null) }

    @Test
    fun lockDarkWrongCode() = capture("lock-dark-wrong", dark = true) {
        Lock(error = "Code erroné. Encore 3 essais avant la déconnexion.")
    }

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

@Composable
private fun Conversations() {
    EngineConversationsScreen(
        state = EngineChatsState(chats = inboxOrder(CHATS)),
        onBack = {},
        onOpenDrawer = {},
        onOpenChat = {},
        onRefresh = {},
    )
}

@Composable
private fun Settings() {
    EngineSettingsScreen(
        state = EngineSettingsUiState(
            addresses = EngineAccess(
                baseUrl = "https://agent.hobbitton.at",
                schedulerUrl = "https://sched.hobbitton.at",
                issuerUrl = "https://auth.hobbitton.at",
            ),
            account = DrawerAccount(name = "Raphaël Nicolle", host = "agent.hobbitton.at"),
        ),
        onBack = {},
        onOpenInstructions = {},
        onOpenUsage = {},
        onThemeMode = {},
        onSignOut = {},
        lockSection = {
            LockSectionContent(
                state = AppLockUiState(lock = LockState.UNLOCKED, biometrics = true),
                fingerprintAvailable = true,
                onChangeCode = {},
                onFingerprint = {},
                onDelay = {},
            )
        },
    )
}

@Composable
private fun Budget(day: String = "15", problem: BudgetProblem? = null) {
    EngineBudgetContent(
        state = EngineBudgetUiState(
            loading = false,
            day = day,
            week = "45",
            month = "200",
            stored = ChatBudget(day = 15.0, week = 45.0, month = 200.0),
            problem = problem,
        ),
        actions = BudgetActions(),
    )
}

@Composable
private fun Drawer() {
    ModalNavigationDrawer(
        drawerState = rememberDrawerState(DrawerValue.Open),
        drawerContent = {
            ModalDrawerSheet {
                EngineDrawerContent(
                    state = EngineChatsState(chats = CHATS.filterNot { it.scheduled }),
                    activeSessionId = "c1",
                    onNewChat = {},
                    onOpenChat = {},
                    onOpenConversations = {},
                    onOpenTasks = {},
                    onOpenSettings = {},
                    onRetry = {},
                    account = DrawerAccount(name = "Raphaël Nicolle", host = "agent.hobbitton.at"),
                )
            }
        },
    ) {
        Surface(Modifier.fillMaxSize()) {}
    }
}

private const val NOW = 1_791_700_000_000L
private const val HOUR = 3_600_000L

private val CHATS = listOf(
    EngineChatSummary("c1", "Résumé des e-mails du matin", NOW - 5 * 60_000, running = true),
    EngineChatSummary("c2", "Agenda de la semaine", NOW - 3 * HOUR, running = false, unread = true),
    EngineChatSummary("r1", "Point mails matin", NOW - 4 * HOUR, running = false, kind = EngineSessionKind.TASK, scheduled = true),
    EngineChatSummary("r2", "Suivi dossiers ouverts", NOW - 20 * 60_000, running = false, kind = EngineSessionKind.TASK, scheduled = true),
    EngineChatSummary("r3", "Guet reponses", NOW - 50 * 60_000, running = false, kind = EngineSessionKind.TASK, scheduled = true, unread = true),
    EngineChatSummary("t1", "Vente Audi Q5 : relance des acheteurs", NOW - 26 * HOUR, running = false, kind = EngineSessionKind.TASK),
    EngineChatSummary("c3", "Devis assurance Matmut", NOW - 3 * 24 * HOUR, running = false),
    EngineChatSummary("c4", "Idées cadeau pour Lucie", NOW - 12 * 24 * HOUR, running = false),
)

@Composable
private fun Lock(error: String?) {
    LockSurface {
        CodeEntry(
            title = "Eva est verrouillée",
            hint = "Entrez votre code",
            error = error,
            onFingerprint = {},
            onComplete = {},
        )
    }
}
