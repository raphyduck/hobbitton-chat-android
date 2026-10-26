package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.TranscriptionOutcome
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Un message tapé pendant que la session travaille part tout de suite (23/10/2026) : le moteur
 * l'enregistre et le tour en cours le lit à la fin de l'étape en cours, sans être interrompu.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MissionChatQueuedSendTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /** The first turn runs until the test lets it end. */
    private val firstTurn = CompletableDeferred<Unit>()

    private val repository = mockk<EngineMissionRepository>(relaxed = true).also {
        coEvery { it.models() } throws IllegalStateException("pas de catalogue")
        coEvery { it.connectors() } throws IllegalStateException("pas de catalogue")
        coEvery { it.history(any(), any()) } returns emptyList()
        every { it.events(any()) } returns emptyFlow()
        coEvery { it.sendMessage("ses_1", "premier", any(), any()) } coAnswers {
            firstTurn.await()
            emptyList()
        }
    }
    private val settings = mockk<SettingsDataStore>().also {
        every { it.chatFontSize } returns emptyFlow()
        every { it.localDictation } returns emptyFlow()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = MissionChatViewModel(
        sessionId = "ses_1",
        repository = repository,
        modelPrices = mockk(relaxed = true),
        settings = settings,
        transcriber = { _, _, _ -> TranscriptionOutcome.Heard("") },
        attention = mockk(relaxed = true),
        ioDispatcher = dispatcher,
    )

    @Test
    fun `a message typed while the turn runs goes out at once`() {
        coEvery { repository.sendMessage("ses_1", "second", any(), any()) } returns emptyList()
        val vm = viewModel()
        vm.onInputChange("premier")
        vm.send()
        assertThat(vm.uiState.value.sending).isTrue()

        vm.onInputChange("second")
        vm.send()

        coVerify { repository.sendMessage("ses_1", "second", any(), any()) }
        assertThat(vm.uiState.value.input).isEmpty()
        // Still running: the first turn has not ended.
        assertThat(vm.uiState.value.sending).isTrue()

        firstTurn.complete(Unit)
        assertThat(vm.uiState.value.sending).isFalse()
    }

    @Test
    fun `a message the engine never recorded comes back to the composer`() {
        coEvery { repository.sendMessage("ses_1", "second", any(), any()) } throws
            IllegalStateException("injoignable")
        val vm = viewModel()
        vm.onInputChange("premier")
        vm.send()

        vm.onInputChange("second")
        vm.send()

        assertThat(vm.uiState.value.input).isEqualTo("second")
        assertThat(vm.uiState.value.sendError).isNotNull()
        assertThat(vm.uiState.value.sending).isTrue()
        firstTurn.complete(Unit)
    }
}
