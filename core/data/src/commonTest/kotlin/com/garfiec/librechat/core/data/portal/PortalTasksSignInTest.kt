package com.garfiec.librechat.core.data.portal

import com.garfiec.librechat.core.data.engine.EngineCallbackDelivery
import com.garfiec.librechat.core.data.engine.EngineSignInLauncher
import com.garfiec.librechat.core.data.engine.EngineSignInProgress
import com.garfiec.librechat.core.data.engine.EngineSignInResult
import com.garfiec.librechat.core.network.engine.EngineAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tasks half of the single sign-in (D-076) is the existing round trip, hosted by a web view:
 * these pin that it delegates rather than reimplements, and what it catches from the web view.
 */
class PortalTasksSignInTest {

    private class FakeLauncher : EngineSignInLauncher {
        val state = MutableStateFlow<EngineSignInProgress>(EngineSignInProgress.Idle)
        var opener: ((String) -> Unit)? = null
        var cancelled = 0
        override val etat: StateFlow<EngineSignInProgress> = state
        override fun lancer(ouvrirNavigateur: (url: String) -> Unit) {
            opener = ouvrirNavigateur
            state.value = EngineSignInProgress.EnCours
        }

        override fun annuler() {
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

    private val configured = EngineAccess(
        baseUrl = "https://agent.example.com",
        issuerUrl = "https://auth.example.com",
        schedulerUrl = "https://sched.example.com",
    )

    private fun subject(
        access: EngineAccess? = configured,
        launcher: FakeLauncher = FakeLauncher(),
        delivery: FakeDelivery = FakeDelivery(),
    ) = PortalTasksSignIn(access = { access }, launcher = launcher, delivery = delivery)

    @Test
    fun `ready only with an engine, a portal and a way back`() = runTest {
        assertTrue(subject().isReady())
        assertFalse(subject(access = null).isReady())
        // The code comes back through the scheduler's route: without its address, no sign-in.
        assertFalse(subject(access = configured.copy(schedulerUrl = "")).isReady())
        assertFalse(subject(access = configured.copy(issuerUrl = "")).isReady())
    }

    @Test
    fun `the portal's page opens where the caller says, not in a browser`() {
        val launcher = FakeLauncher()
        val shown = mutableListOf<String>()

        subject(launcher = launcher).start { shown += it }
        launcher.opener?.invoke("https://auth.example.com/api/oidc/authorization?request_uri=urn")

        assertEquals(listOf("https://auth.example.com/api/oidc/authorization?request_uri=urn"), shown)
        assertEquals(EngineSignInProgress.EnCours, launcher.state.value)
    }

    @Test
    fun `the app-scheme hop is caught and dropped in the mailbox`() {
        val delivery = FakeDelivery()
        val handoff = subject(delivery = delivery)

        assertTrue(handoff.offer("at.hobbitton.chat://oauth?code=c&state=s"))
        assertFalse(handoff.offer("https://sched.example.com/oauth/authelia"))

        assertEquals(listOf("at.hobbitton.chat://oauth?code=c&state=s"), delivery.delivered)
    }

    @Test
    fun `closing the web view cancels the round trip at once`() {
        val launcher = FakeLauncher()
        val handoff = subject(launcher = launcher)
        handoff.start { }

        handoff.cancel()

        assertEquals(1, launcher.cancelled)
        assertEquals(EngineSignInProgress.Termine(EngineSignInResult.Cancelled), handoff.progress.value)
        handoff.acknowledge()
        assertEquals(EngineSignInProgress.Idle, handoff.progress.value)
    }
}
