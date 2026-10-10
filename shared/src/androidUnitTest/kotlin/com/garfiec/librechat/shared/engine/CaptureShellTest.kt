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
import com.garfiec.librechat.core.network.engine.EngineAccess
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The drawer and the settings, rendered to `build/captures/` (`:shared:recordRoborazziDebug`). */
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
    fun settingsLight() = capture("settings-light", dark = false) {
        EngineSettingsScreen(
            state = EngineSettingsUiState(
                addresses = EngineAccess(
                    baseUrl = "https://agent.hobbitton.at",
                    schedulerUrl = "https://sched.hobbitton.at",
                    issuerUrl = "https://auth.hobbitton.at",
                ),
            ),
            onBack = {},
            onOpenInstructions = {},
            onOpenUsage = {},
            onThemeMode = {},
            onSignOut = {},
        )
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
private fun Drawer() {
    ModalNavigationDrawer(
        drawerState = rememberDrawerState(DrawerValue.Open),
        drawerContent = {
            ModalDrawerSheet {
                EngineDrawerContent(
                    state = EngineChatsState(chats = CHATS),
                    activeSessionId = "c1",
                    onNewChat = {},
                    onOpenChat = {},
                    onOpenTasks = {},
                    onOpenSettings = {},
                    onRetry = {},
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
    EngineChatSummary("c2", "Agenda de la semaine", NOW - 3 * HOUR, running = false),
    EngineChatSummary("t1", "Vente Audi Q5 : relance des acheteurs", NOW - 26 * HOUR, running = false, kind = EngineSessionKind.TASK),
    EngineChatSummary("c3", "Devis assurance Matmut", NOW - 3 * 24 * HOUR, running = false),
    EngineChatSummary("c4", "Idées cadeau pour Lucie", NOW - 12 * 24 * HOUR, running = false),
)
