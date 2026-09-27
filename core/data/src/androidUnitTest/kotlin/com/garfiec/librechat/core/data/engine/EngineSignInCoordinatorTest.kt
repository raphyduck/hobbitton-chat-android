package com.garfiec.librechat.core.data.engine

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Cancelling the portal round trip (D-076). The web view that hosts it can be closed; the round
 * trip must end then, not five minutes later, and a new one must be possible right away.
 */
class EngineSignInCoordinatorTest {

    @Test
    fun `cancelling ends the round trip at once and says so`() = runTest {
        val portail = mockk<EngineSignIn>()
        coEvery { portail.signIn(any()) } coAnswers { awaitCancellation() }
        val coordinator = EngineSignInCoordinator(portail, backgroundScope)

        coordinator.lancer { }
        runCurrent()
        assertThat(coordinator.etat.value).isEqualTo(EngineSignInProgress.EnCours)

        coordinator.annuler()
        runCurrent()

        // One outcome, `Cancelled` — not overwritten by the cancelled coroutine's own ending.
        assertThat(coordinator.etat.value).isEqualTo(EngineSignInProgress.Termine(EngineSignInResult.Cancelled))
    }

    @Test
    fun `after a cancel, a new round trip starts`() = runTest {
        val portail = mockk<EngineSignIn>()
        coEvery { portail.signIn(any()) } coAnswers { awaitCancellation() }
        val coordinator = EngineSignInCoordinator(portail, backgroundScope)

        coordinator.lancer { }
        runCurrent()
        coordinator.annuler()
        coordinator.acquitter()
        coordinator.lancer { }
        runCurrent()

        assertThat(coordinator.etat.value).isEqualTo(EngineSignInProgress.EnCours)
        coVerify(exactly = 2) { portail.signIn(any()) }
    }

    @Test
    fun `cancelling with nothing in flight changes nothing`() = runTest {
        val coordinator = EngineSignInCoordinator(mockk(), backgroundScope)

        coordinator.annuler()

        assertThat(coordinator.etat.value).isEqualTo(EngineSignInProgress.Idle)
    }
}
