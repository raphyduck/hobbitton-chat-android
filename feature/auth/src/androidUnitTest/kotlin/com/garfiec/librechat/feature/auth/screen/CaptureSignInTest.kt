package com.garfiec.librechat.feature.auth.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.auth.viewmodel.PortalLoginUiState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sign-in, rendered to `build/captures/` (`-Pcaptures`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-rFR-w411dp-h891dp-port-420dpi")
class CaptureSignInTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun signInLight() = capture("sign-in-light", dark = false) { Form(PREFILLED) }

    @Test
    fun signInDark() = capture("sign-in-dark", dark = true) { Form(PREFILLED) }

    @Test
    fun signInAddresses() = capture("sign-in-addresses", dark = false) {
        Form(PREFILLED.copy(addressesShown = true))
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
private fun Form(state: PortalLoginUiState) {
    PortalAddressForm(
        state = state,
        onBaseUrl = {},
        onSchedulerUrl = {},
        onIssuerUrl = {},
        onStart = {},
    )
}

private val PREFILLED = PortalLoginUiState(
    available = true,
    baseUrl = "https://agent.example.com",
    schedulerUrl = "https://sched.example.com",
    issuerUrl = "https://auth.example.com",
    prefilled = true,
    addressesShown = false,
)
