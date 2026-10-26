package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.engine.ConversationModelStore
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.TranscriptionOutcome
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
 * Le modèle d'une conversation est celui que cet appareil lui a envoyé en dernier (26/10/2026,
 * Raphaël : « le choix du modèle change en cours de route »), pas celui du dernier tour, qu'un
 * rappel ou une relance du planificateur a pu jouer sur un autre modèle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MissionChatModelMemoryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val deepseek = EngineModelRef("hobbitton-chat", "deepseek-v4-pro")
    private val sonnet = EngineModelRef("hobbitton-chat", "claude-sonnet-5-5")

    /** The last turn, a reminder's, ran on Sonnet. */
    private val repository = mockk<EngineMissionRepository>(relaxed = true).also {
        coEvery { it.cachedHistory(any()) } returns null
        coEvery { it.models() } throws IllegalStateException("pas de catalogue")
        coEvery { it.connectors() } throws IllegalStateException("pas de catalogue")
        coEvery { it.history(any(), any()) } returns listOf(
            EngineStreamEvent.MessageStarted("m1", "user"),
            EngineStreamEvent.MessageStarted("m2", "assistant", model = deepseek),
            EngineStreamEvent.MessageStarted("m3", "user"),
            EngineStreamEvent.MessageStarted("m4", "assistant", model = sonnet),
        )
        every { it.events(any()) } returns emptyFlow()
        coEvery { it.sendMessage(any(), any(), any(), any()) } returns emptyList()
    }
    private val settings = mockk<SettingsDataStore>().also {
        every { it.chatFontSize } returns emptyFlow()
        every { it.localDictation } returns emptyFlow()
    }
    private val memory = mockk<ConversationModelStore>(relaxed = true)

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
        conversationModels = memory,
    )

    @Test
    fun `the model this device chose for the conversation stays ahead of the last turn's`() {
        coEvery { memory.modelOf("ses_1") } returns deepseek
        val vm = viewModel()

        assertThat(vm.uiState.value.chat.model).isEqualTo(sonnet)
        assertThat(vm.uiState.value.effectiveModel?.ref).isEqualTo(deepseek)

        vm.onInputChange("et ensuite ?")
        vm.send()

        coVerify { repository.sendMessage("ses_1", "et ensuite ?", deepseek, any()) }
        coVerify { memory.record("ses_1", deepseek) }
    }

    @Test
    fun `without a memory the last turn's model stands, and a send records it`() {
        coEvery { memory.modelOf("ses_1") } returns null
        val vm = viewModel()

        assertThat(vm.uiState.value.effectiveModel?.ref).isEqualTo(sonnet)

        vm.onInputChange("continue")
        vm.send()

        coVerify { repository.sendMessage("ses_1", "continue", sonnet, any()) }
        coVerify { memory.record("ses_1", sonnet) }
    }
}
