package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.TranscriptionOutcome
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
        // A relaxed mock would answer a mock tail: no conversation was kept unless a test says so.
        coEvery { it.cachedHistory(any()) } returns null
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
        coEvery { repository.history("ses_1", any()) } coAnswers {
            if (reads++ == 0) awaitCancellation() else emptyList()
        }
        val vm = viewModel()
        assertThat(vm.uiState.value.loadingHistory).isTrue()

        dispatcher.scheduler.advanceTimeBy(11_000)

        assertThat(vm.uiState.value.loadingHistory).isFalse()
        assertThat(vm.uiState.value.historyError).isNull()
        coVerify(exactly = 2) { repository.history("ses_1", any()) }
        // 26/10/2026 : l'essai suspendu dit que la connexion est morte ; on la jette avant le suivant.
        verify(exactly = 1) { repository.dropConnections() }
    }

    @Test
    fun `a conversation opened before shows its kept tail at once and asks the engine only if it moved`() {
        // 26/10/2026, Raphaël : « quand tu reviens dans une conversation qu'on a déjà ouverte, il
        // ne redemande pas nécessairement le reste ».
        val kept = EngineMissionRepository.CachedHistory(
            events = listOf(
                EngineStreamEvent.MessageStarted("m1", "user"),
                EngineStreamEvent.PartDelta("m1", "p1", "text", "salut"),
            ),
            messageCount = 1,
            limit = HISTORY_TAIL,
            updated = 1_000L,
        )
        coEvery { repository.cachedHistory("ses_1") } returns kept
        coEvery { repository.historyUnlessUpdatedAt("ses_1", HISTORY_TAIL, 1_000L) } returns null
        val vm = viewModel()

        assertThat(vm.uiState.value.loadingHistory).isFalse()
        assertThat(vm.uiState.value.chat.turns.map { it.key }).isEqualTo(listOf("m1"))
        assertThat(vm.uiState.value.historyTruncated).isFalse()
        coVerify(exactly = 0) { repository.history(any(), any()) }
        coVerify(exactly = 1) { repository.models() }

        // The session moved: the engine's tail is folded over the kept one.
        coEvery { repository.cachedHistory("ses_1") } returns kept
        coEvery { repository.historyUnlessUpdatedAt("ses_1", HISTORY_TAIL, 1_000L) } returns listOf(
            EngineStreamEvent.MessageStarted("m2", "assistant"),
        )
        val moved = viewModel()
        assertThat(moved.uiState.value.chat.turns.map { it.key }).isEqualTo(listOf("m1", "m2"))

        // A refresh reads the engine whatever the device kept.
        coEvery { repository.history("ses_1", any()) } returns emptyList()
        moved.refresh()
        coVerify(exactly = 1) { repository.history("ses_1", any()) }
    }

    @Test
    fun `the catalogue waits for the transcript`() {
        // 26/10/2026 : modèles, connecteurs, niveau et tarifs partaient avec la transcription et lui
        // prenaient sa bande passante ; l'écran, c'est la transcription.
        coEvery { repository.history("ses_1", any()) } coAnswers {
            if (reads++ == 0) awaitCancellation() else emptyList()
        }
        val vm = viewModel()
        coVerify(exactly = 0) { repository.models() }
        coVerify(exactly = 0) { repository.connectors() }

        dispatcher.scheduler.advanceTimeBy(11_000)

        assertThat(vm.uiState.value.loadingHistory).isFalse()
        coVerify(exactly = 1) { repository.models() }
        coVerify(exactly = 1) { repository.connectors() }
    }

    @Test
    fun `reads that keep hanging end on an error, not on the placeholders`() {
        coEvery { repository.history("ses_1", any()) } coAnswers { awaitCancellation() }
        val vm = viewModel()

        dispatcher.scheduler.advanceTimeBy(31_000)

        assertThat(vm.uiState.value.loadingHistory).isFalse()
        assertThat(vm.uiState.value.historyError).isNotNull()
        coVerify(exactly = 3) { repository.history("ses_1", any()) }
    }

    @Test
    fun `a conversation opens on its last messages, the rest on demand`() {
        // 26/10/2026, Raphaël : changer de conversation prenait trop de temps ; la plus grosse pesait
        // 2,7 Mo entière, 23 Ko pour ses trois derniers messages.
        fun message(i: Int) = listOf(
            EngineStreamEvent.MessageStarted("m$i", if (i % 2 == 1) "user" else "assistant"),
            EngineStreamEvent.PartDelta("m$i", "p$i", "text", "message $i"),
        )
        coEvery { repository.history("ses_1", HISTORY_TAIL) } returns (11..50).flatMap(::message)
        coEvery { repository.history("ses_1", null) } returns (1..50).flatMap(::message)
        val vm = viewModel()

        assertThat(vm.uiState.value.chat.turns.map { it.key }).isEqualTo((11..50).map { "m$it" })
        assertThat(vm.uiState.value.historyTruncated).isTrue()

        vm.loadEarlierHistory()

        assertThat(vm.uiState.value.chat.turns.map { it.key }).isEqualTo((1..50).map { "m$it" })
        assertThat(vm.uiState.value.historyTruncated).isFalse()
        assertThat(vm.uiState.value.loadingEarlier).isFalse()
        coVerify(exactly = 1) { repository.history("ses_1", null) }
    }

    @Test
    fun `fewer messages than asked for means the whole conversation is already on screen`() {
        coEvery { repository.history("ses_1", HISTORY_TAIL) } returns listOf(EngineStreamEvent.MessageStarted("m1", "user"))
        val vm = viewModel()

        assertThat(vm.uiState.value.historyTruncated).isFalse()
        vm.loadEarlierHistory()
        coVerify(exactly = 0) { repository.history("ses_1", null) }
    }
}
