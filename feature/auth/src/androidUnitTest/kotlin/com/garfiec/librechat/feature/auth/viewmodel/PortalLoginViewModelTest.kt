package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.data.engine.EngineAddressField
import com.garfiec.librechat.core.data.engine.EngineCallbackDelivery
import com.garfiec.librechat.core.data.engine.EngineSettingsStore
import com.garfiec.librechat.core.data.engine.EngineSignInLauncher
import com.garfiec.librechat.core.data.engine.EngineSignInProgress
import com.garfiec.librechat.core.data.engine.EngineSignInResult
import com.garfiec.librechat.core.data.portal.PortalTasksSignIn
import com.garfiec.librechat.core.network.engine.EngineAccess
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The only sign-in left (D-077): three addresses, then the portal's round trip in the app's web
 * view — no LibreChat step, no cookie read, no chat session opened on the side.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PortalLoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val settings = mockk<EngineSettingsStore>(relaxed = true)

    private class FakeLauncher : EngineSignInLauncher {
        val state = MutableStateFlow<EngineSignInProgress>(EngineSignInProgress.Idle)
        var opener: ((String) -> Unit)? = null
        var started = 0
        var cancelled = 0
        override val etat: StateFlow<EngineSignInProgress> = state
        override fun lancer(ouvrirNavigateur: (url: String) -> Unit) {
            started++
            opener = ouvrirNavigateur
            state.value = EngineSignInProgress.EnCours
        }

        override fun annuler() {
            if (state.value != EngineSignInProgress.EnCours) return
            cancelled++
            state.value = EngineSignInProgress.Termine(EngineSignInResult.Cancelled)
        }

        override fun acquitter() {
            if (state.value is EngineSignInProgress.Termine) state.value = EngineSignInProgress.Idle
        }
    }

    private class FakeDelivery : EngineCallbackDelivery {
        val delivered = mutableListOf<String>()
        override fun deposer(uri: String): Boolean {
            delivered += uri
            return true
        }
    }

    private val launcher = FakeLauncher()
    private val delivery = FakeDelivery()
    private var engine: EngineAccess? = EngineAccess(
        baseUrl = "https://agent.example.com",
        issuerUrl = "https://auth.example.com",
        schedulerUrl = "https://sched.example.com",
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { settings.baseUrl } returns flowOf("")
        every { settings.issuerUrl } returns flowOf("")
        every { settings.schedulerUrl } returns flowOf("")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(withEngine: Boolean = true) = PortalLoginViewModel(
        settings = if (withEngine) settings else null,
        tasks = if (withEngine) PortalTasksSignIn(access = { engine }, launcher = launcher, delivery = delivery) else null,
    )

    private fun PortalLoginViewModel.fillIn() {
        onBaseUrl("https://agent.example.com")
        onSchedulerUrl("https://sched.example.com")
        onIssuerUrl("https://auth.example.com")
    }

    @Test
    fun `the form is offered where the engine graph exists`() {
        assertThat(viewModel().state.value.available).isTrue()
        assertThat(viewModel(withEngine = false).state.value.available).isFalse()
    }

    @Test
    fun `the stored addresses fill the form`() = runTest {
        every { settings.baseUrl } returns flowOf("https://agent.example.com")
        every { settings.issuerUrl } returns flowOf("https://auth.example.com")
        every { settings.schedulerUrl } returns flowOf("https://sched.example.com")

        val subject = viewModel()
        advanceUntilIdle()

        assertThat(subject.state.value.baseUrl).isEqualTo("https://agent.example.com")
        assertThat(subject.state.value.issuerUrl).isEqualTo("https://auth.example.com")
        assertThat(subject.state.value.schedulerUrl).isEqualTo("https://sched.example.com")
    }

    @Test
    fun `all three addresses are required, the scheduler included`() = runTest {
        val subject = viewModel()
        subject.onBaseUrl("agent.example.com")
        subject.onIssuerUrl("https://auth.example.com")

        subject.start()
        advanceUntilIdle()

        assertThat(subject.state.value.invalid)
            .containsExactly(EngineAddressField.BASE_URL, EngineAddressField.SCHEDULER_URL)
        assertThat(subject.state.value.step).isEqualTo(PortalLoginStep.Idle)
        assertThat(launcher.started).isEqualTo(0)
        coVerify(exactly = 0) { settings.save(any(), any(), any()) }
    }

    @Test
    fun `one round trip saves the addresses, opens the portal and signs in`() = runTest {
        val subject = viewModel()
        subject.fillIn()

        subject.start()
        advanceUntilIdle()

        coVerify { settings.save("https://agent.example.com", "https://auth.example.com", "https://sched.example.com") }
        assertThat(subject.state.value.step).isEqualTo(PortalLoginStep.Portal)
        assertThat(launcher.started).isEqualTo(1)

        // The authorization opens in the app's web view…
        launcher.opener!!.invoke("https://auth.example.com/api/oidc/authorization?request_uri=urn")
        assertThat(subject.state.value.page).isEqualTo("https://auth.example.com/api/oidc/authorization?request_uri=urn")
        // …its pages load there, and the scheduler's hop to the app scheme is caught, not loaded.
        assertThat(subject.onNavigation("https://auth.example.com/")).isFalse()
        assertThat(subject.onNavigation("at.hobbitton.chat://oauth?code=c&state=s")).isTrue()
        assertThat(delivery.delivered).containsExactly("at.hobbitton.chat://oauth?code=c&state=s")

        launcher.state.value = EngineSignInProgress.Termine(EngineSignInResult.Authorized)
        advanceUntilIdle()

        assertThat(subject.state.value.signedIn).isTrue()
        assertThat(subject.state.value.step).isEqualTo(PortalLoginStep.Idle)
        assertThat(subject.state.value.page).isNull()
        assertThat(launcher.state.value).isEqualTo(EngineSignInProgress.Idle)
    }

    @Test
    fun `a second sign-in after a sign-out is heard`() = runTest {
        val subject = viewModel()
        subject.fillIn()
        subject.start()
        advanceUntilIdle()
        launcher.state.value = EngineSignInProgress.Termine(EngineSignInResult.Authorized)
        advanceUntilIdle()
        subject.consumeSignedIn()
        assertThat(subject.state.value.signedIn).isFalse()

        subject.start()
        advanceUntilIdle()
        launcher.state.value = EngineSignInProgress.Termine(EngineSignInResult.Authorized)
        advanceUntilIdle()

        assertThat(subject.state.value.signedIn).isTrue()
    }

    @Test
    fun `no LibreChat step - the first page is the portal's, never a chat server's`() = runTest {
        val subject = viewModel()
        subject.fillIn()
        subject.start()
        advanceUntilIdle()

        // Nothing is loaded before the round trip names its page: no `/oauth/openid` detour.
        assertThat(subject.state.value.page).isNull()
    }

    @Test
    fun `closing the web view cancels the round trip without an error`() = runTest {
        val subject = viewModel()
        subject.fillIn()
        subject.start()
        advanceUntilIdle()

        subject.cancel()
        advanceUntilIdle()

        assertThat(launcher.cancelled).isEqualTo(1)
        assertThat(subject.state.value.step).isEqualTo(PortalLoginStep.Idle)
        assertThat(subject.state.value.problem).isNull()
        assertThat(subject.state.value.signedIn).isFalse()
    }

    @Test
    fun `a refusal is reported and leaves the person signed out`() = runTest {
        val subject = viewModel()
        subject.fillIn()
        subject.start()
        advanceUntilIdle()

        launcher.state.value = EngineSignInProgress.Termine(EngineSignInResult.Refused("access_denied", null))
        advanceUntilIdle()

        assertThat(subject.state.value.problem).isEqualTo(PortalLoginProblem.REFUSED)
        assertThat(subject.state.value.signedIn).isFalse()
    }

    @Test
    fun `addresses the round trip cannot use are reported before anything opens`() = runTest {
        engine = null
        val subject = viewModel()
        subject.fillIn()

        subject.start()
        advanceUntilIdle()

        assertThat(subject.state.value.problem).isEqualTo(PortalLoginProblem.NOT_READY)
        assertThat(launcher.started).isEqualTo(0)
    }

    @Test
    fun `a stale outcome left by the Tasks tab is not read as this one's`() = runTest {
        launcher.state.value = EngineSignInProgress.Termine(EngineSignInResult.Authorized)
        val subject = viewModel()
        subject.fillIn()

        subject.start()
        advanceUntilIdle()

        assertThat(subject.state.value.signedIn).isFalse()
        assertThat(subject.state.value.step).isEqualTo(PortalLoginStep.Portal)
    }

    @Test
    fun `every failure maps to a sentence`() {
        assertThat(problemOf(EngineSignInResult.NotConfigured)).isEqualTo(PortalLoginProblem.NOT_READY)
        assertThat(problemOf(EngineSignInResult.NoCallbackHost)).isEqualTo(PortalLoginProblem.NOT_READY)
        assertThat(problemOf(EngineSignInResult.PortalUnreachable("dns"))).isEqualTo(PortalLoginProblem.UNREACHABLE)
        assertThat(problemOf(EngineSignInResult.MissingAuthorizationScope(emptyList())))
            .isEqualTo(PortalLoginProblem.REFUSED)
        assertThat(problemOf(EngineSignInResult.Interrupted("exchange"))).isEqualTo(PortalLoginProblem.INTERRUPTED)
    }
}
