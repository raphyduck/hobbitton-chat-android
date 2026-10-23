package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.TranscriptionOutcome
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Une conversation restait parfois sur ses lignes grises jusqu'à ce qu'on la quitte et la
 * rouvre (23/10/2026) : une lecture de l'historique qui ne revient pas est redemandée.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MissionChatHistoryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private var reads = 0

    private val repository = mockk<EngineMissionRepository>(relaxed = true).also {
        coEvery { it.models() } throws IllegalStateException("pas de catalogue")
        coEvery { it.connectors() } throws IllegalStateException("pas de catalogue")
        every { it.events(any()) } returns emptyFlow()
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
    fun `a read that hangs is asked again`() {
        coEvery { repository.history("ses_1") } coAnswers {
            if (reads++ == 0) awaitCancellation() else emptyList()
        }
        val vm = viewModel()
        assertThat(vm.uiState.value.loadingHistory).isTrue()

        dispatcher.scheduler.advanceTimeBy(11_000)

        assertThat(vm.uiState.value.loadingHistory).isFalse()
        assertThat(vm.uiState.value.historyError).isNull()
        coVerify(exactly = 2) { repository.history("ses_1") }
    }

    @Test
    fun `reads that keep hanging end on an error, not on the placeholders`() {
        coEvery { repository.history("ses_1") } coAnswers { awaitCancellation() }
        val vm = viewModel()

        dispatcher.scheduler.advanceTimeBy(31_000)

        assertThat(vm.uiState.value.loadingHistory).isFalse()
        assertThat(vm.uiState.value.historyError).isNotNull()
        coVerify(exactly = 3) { repository.history("ses_1") }
    }
}
