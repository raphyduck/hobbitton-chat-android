package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.engine.EngineCallbackDelivery
import com.garfiec.librechat.core.data.engine.EngineSignInLauncher
import com.garfiec.librechat.core.data.engine.EngineSignInProgress
import com.garfiec.librechat.core.data.engine.EngineSignInResult
import com.garfiec.librechat.core.data.portal.PortalTasksSignIn
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.network.engine.EngineAccess
import com.garfiec.librechat.feature.auth.oauth.OAuthLauncher
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The single sign-in (D-076): one web view, the chat's session from its cookie, then the tasks'
 * consent in the same view — and the screen left only once both are done.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PortalLoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val oAuthLauncher = mockk<OAuthLauncher>(relaxed = true)
    private val serverDataStore = mockk<ServerDataStore>(relaxed = true)
    private val accountSwitcher = mockk<AccountSwitcher>(relaxed = true)

    private val configFlow = MutableStateFlow<StartupConfig?>(null)

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
        every { configRepository.startupConfig } returns configFlow
        every { accountSwitcher.pendingAdd } returns null
        every { serverDataStore.getBaseUrl() } returns "https://chat.example.com/"
        every { oAuthLauncher.embedsPortal } returns true
        every { oAuthLauncher.extractTokenFromCookies(any()) } returns "refresh-from-cookie"
        coEvery { authRepository.loginWithOAuthToken(any()) } returns Result.Success(mockk<User>(relaxed = true))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(withTasks: Boolean = true) = PortalLoginViewModel(
        authRepository = authRepository,
        configRepository = configRepository,
        oAuthLauncher = oAuthLauncher,
        serverDataStore = serverDataStore,
        accountSwitcher = accountSwitcher,
        tasks = if (withTasks) PortalTasksSignIn(access = { engine }, launcher = launcher, delivery = delivery) else null,
    )

    private fun PortalLoginViewModel.returnToChat() {
        assertThat(onNavigation("https://chat.example.com/oauth/openid/callback?code=c&state=s")).isFalse()
        assertThat(onNavigation("https://chat.example.com/")).isTrue()
    }

    @Test
    fun `the portal is offered when the server has openid and the platform can host it`() = runTest {
        val subject = viewModel()
        configFlow.value = StartupConfig(
            socialLoginEnabled = true,
            socialLogins = listOf("openid"),
            openidLabel = "Se connecter avec hobbitton",
        )
        advanceUntilIdle()

        assertThat(subject.state.value.offered).isTrue()
        assertThat(subject.state.value.label).isEqualTo("Se connecter avec hobbitton")
    }

    @Test
    fun `no portal where the platform cannot read the web view's cookies`() = runTest {
        every { oAuthLauncher.embedsPortal } returns false
        val subject = viewModel()
        configFlow.value = StartupConfig(socialLoginEnabled = true, socialLogins = listOf("openid"))
        advanceUntilIdle()

        assertThat(subject.state.value.offered).isFalse()
    }

    @Test
    fun `start opens the server's own oauth route and drops a stale cookie first`() = runTest {
        val subject = viewModel()

        subject.start()

        assertThat(subject.state.value.step).isEqualTo(PortalLoginStep.Portal)
        // `/oauth/openid`, not the upstream client's `/api/oauth/openid`.
        assertThat(subject.state.value.page).isEqualTo("https://chat.example.com/oauth/openid")
        verify { oAuthLauncher.clearOAuthCookie("https://chat.example.com") }
    }

    @Test
    fun `one round trip signs into the chat, then the tasks, then leaves`() = runTest {
        val subject = viewModel()
        subject.start()

        subject.returnToChat()
        advanceUntilIdle()

        // The chat's session comes from the cookie, through the ordinary OAuth path.
        coVerify { authRepository.loginWithOAuthToken("refresh-from-cookie") }
        assertThat(subject.state.value.step).isEqualTo(PortalLoginStep.Tasks)
        assertThat(subject.state.value.signedIn).isFalse()

        // The tasks' authorization opens in the same web view…
        launcher.opener!!.invoke("https://auth.example.com/api/oidc/authorization?request_uri=urn")
        assertThat(subject.state.value.page).isEqualTo("https://auth.example.com/api/oidc/authorization?request_uri=urn")
        // …and its return to the app scheme is caught there, not loaded.
        assertThat(subject.onNavigation("at.hobbitton.chat://oauth?code=c2&state=s2")).isTrue()
        assertThat(delivery.delivered).containsExactly("at.hobbitton.chat://oauth?code=c2&state=s2")

        launcher.state.value = EngineSignInProgress.Termine(EngineSignInResult.Authorized)
        advanceUntilIdle()

        assertThat(subject.state.value.signedIn).isTrue()
        assertThat(subject.state.value.step).isEqualTo(PortalLoginStep.Idle)
        assertThat(launcher.state.value).isEqualTo(EngineSignInProgress.Idle)
        // Read once, then gone from the jar.
        verify(atLeast = 2) { oAuthLauncher.clearOAuthCookie("https://chat.example.com") }
    }

    @Test
    fun `the web client is never loaded, even after the chat's step`() = runTest {
        val subject = viewModel()
        subject.start()
        subject.returnToChat()
        advanceUntilIdle()

        assertThat(subject.onNavigation("https://chat.example.com/c/new")).isTrue()
        coVerify(exactly = 1) { authRepository.loginWithOAuthToken(any()) }
    }

    @Test
    fun `no refresh cookie means no session, and nothing is sent`() = runTest {
        every { oAuthLauncher.extractTokenFromCookies(any()) } returns null
        val subject = viewModel()
        subject.start()

        subject.returnToChat()
        advanceUntilIdle()

        assertThat(subject.state.value.problem).isEqualTo(PortalLoginProblem.NO_SESSION)
        assertThat(subject.state.value.step).isEqualTo(PortalLoginStep.Idle)
        assertThat(subject.state.value.signedIn).isFalse()
        coVerify(exactly = 0) { authRepository.loginWithOAuthToken(any()) }
        assertThat(launcher.started).isEqualTo(0)
    }

    @Test
    fun `a session the server refuses is reported before any consent is asked`() = runTest {
        coEvery { authRepository.loginWithOAuthToken(any()) } returns Result.Error(message = "Refresh token invalid")
        val subject = viewModel()
        subject.start()

        subject.returnToChat()
        advanceUntilIdle()

        assertThat(subject.state.value.problem).isEqualTo(PortalLoginProblem.SESSION_FAILED)
        assertThat(subject.state.value.problemDetail).isEqualTo("Refresh token invalid")
        assertThat(launcher.started).isEqualTo(0)
    }

    @Test
    fun `without a way back for the tasks, the sign-in ends with the chat`() = runTest {
        engine = engine!!.copy(schedulerUrl = "")
        val subject = viewModel()
        subject.start()

        subject.returnToChat()
        advanceUntilIdle()

        assertThat(subject.state.value.signedIn).isTrue()
        assertThat(launcher.started).isEqualTo(0)
    }

    @Test
    fun `without the engine graph, the sign-in ends with the chat`() = runTest {
        val subject = viewModel(withTasks = false)
        subject.start()

        subject.returnToChat()
        advanceUntilIdle()

        assertThat(subject.state.value.signedIn).isTrue()
    }

    @Test
    fun `closing before the chat is signed in abandons everything`() = runTest {
        val subject = viewModel()
        subject.start()

        subject.cancel()

        assertThat(subject.state.value.step).isEqualTo(PortalLoginStep.Idle)
        assertThat(subject.state.value.page).isNull()
        assertThat(subject.state.value.signedIn).isFalse()
        coVerify(exactly = 0) { authRepository.loginWithOAuthToken(any()) }
    }

    @Test
    fun `closing at the consent skips the tasks and keeps the chat`() = runTest {
        val subject = viewModel()
        subject.start()
        subject.returnToChat()
        advanceUntilIdle()

        subject.cancel()
        advanceUntilIdle()

        assertThat(launcher.cancelled).isEqualTo(1)
        assertThat(subject.state.value.signedIn).isTrue()
    }

    @Test
    fun `a round trip left in flight by the Tasks tab is cancelled, not waited on`() = runTest {
        launcher.state.value = EngineSignInProgress.EnCours
        val subject = viewModel()
        subject.start()

        subject.returnToChat()
        advanceUntilIdle()

        assertThat(launcher.cancelled).isEqualTo(1)
        assertThat(launcher.started).isEqualTo(1)
        assertThat(subject.state.value.step).isEqualTo(PortalLoginStep.Tasks)
    }
}
