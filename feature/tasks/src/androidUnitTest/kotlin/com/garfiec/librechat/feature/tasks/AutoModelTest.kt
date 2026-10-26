package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.engine.AudioTranscriber
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineModelChoice
import com.garfiec.librechat.core.data.engine.TranscriptionOutcome
import com.garfiec.librechat.core.data.scheduler.SchedulerRepository
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.core.model.scheduler.ModelTierChoice
import com.garfiec.librechat.core.model.scheduler.ModelTiers
import com.garfiec.librechat.feature.tasks.util.StagedAttachment
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Le mode « Auto » (25/10/2026, règle de Raphaël : le modèle nécessaire et suffisant) : le premier
 * message est lu par le trieur du planificateur, la conversation part sur le modèle de son palier et
 * le garde ; « Refaire plus fort » renvoie la dernière demande sur le palier au-dessus.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val deepseek = EngineSelectableModel("hobbitton-chat", "deepseek-v4-pro", "DeepSeek V4 Pro")
    private val opus = EngineSelectableModel("hobbitton-chat", "claude-opus-5-5", "Claude Opus 5.5")
    private val tiers = ModelTiers(
        models = mapOf("moyen" to "hobbitton-chat/deepseek-v4-pro", "haut" to "hobbitton-chat/claude-opus-5-5"),
        triage = true,
    )

    private val repository = mockk<EngineMissionRepository>(relaxed = true).also {
        coEvery { it.models() } returns EngineModelChoice(
            models = listOf(deepseek, opus),
            preselected = EngineSelectableModel.AUTO,
            engineDefault = opus,
        )
        coEvery { it.connectors() } returns ConnectorCatalogue(connecteurs = emptyMap())
        coEvery { it.startChat(any(), any(), any(), any()) } returns "ses_nouvelle"
    }
    private val scheduler = mockk<SchedulerRepository>(relaxed = true).also {
        coEvery { it.modelTiers() } returns tiers
        coEvery { it.triage(any(), any()) } returns ModelTierChoice(tier = "moyen", model = "hobbitton-chat/deepseek-v4-pro")
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

    private fun viewModel(sessionId: String? = null) = MissionChatViewModel(
        sessionId = sessionId,
        repository = repository,
        modelPrices = mockk(relaxed = true),
        settings = settings,
        transcriber = AudioTranscriber { _, _, _ -> TranscriptionOutcome.Heard("") },
        attention = mockk(relaxed = true),
        ioDispatcher = dispatcher,
        scheduler = scheduler,
    )

    @Test
    fun `a new conversation on Auto starts on the model the trieur names, and the chip shows it`() {
        val vm = viewModel()
        assertThat(vm.uiState.value.effectiveModel).isEqualTo(EngineSelectableModel.AUTO)

        val photo = StagedAttachment(id = "p1", mime = "image/jpeg", filename = "devis.jpg", bytes = byteArrayOf(1))
        vm.addAttachments(listOf(photo))
        vm.onInputChange("Analyse ce devis")
        vm.send()

        coVerify { scheduler.triage("Analyse ce devis", true) }
        coVerify { repository.startChat(text = "Analyse ce devis", connectors = any(), model = deepseek.ref, files = any()) }
        assertThat(vm.uiState.value.model).isEqualTo(deepseek)
    }

    @Test
    fun `without an answer from the trieur the conversation starts on the default, never on Auto`() {
        coEvery { scheduler.triage(any(), any()) } returns null
        val vm = viewModel()

        vm.onInputChange("Bonjour")
        vm.send()

        coVerify { repository.startChat(text = "Bonjour", connectors = any(), model = null, files = any()) }
    }

    @Test
    fun `a conversation that already ran keeps its model, the trieur is not asked`() {
        val ran = EngineModelRef("hobbitton-chat", "deepseek-v4-pro")
        coEvery { repository.history("ses_suivie", any()) } returns listOf(
            EngineStreamEvent.MessageStarted("m1", "user"),
            EngineStreamEvent.MessageStarted("m2", "assistant", ran),
        )
        every { repository.events("ses_suivie") } returns flowOf()
        val vm = viewModel(sessionId = "ses_suivie")
        vm.selectModel(EngineSelectableModel.AUTO)

        vm.onInputChange("Et ensuite ?")
        vm.send()

        coVerify(exactly = 0) { scheduler.triage(any(), any()) }
        coVerify { repository.sendMessage("ses_suivie", "Et ensuite ?", ran, any()) }
    }

    @Test
    fun `redo stronger asks the last request again on the tier above, and stays there`() {
        val ran = EngineModelRef("hobbitton-chat", "deepseek-v4-pro")
        coEvery { repository.history("ses_suivie", any()) } returns listOf(
            EngineStreamEvent.MessageStarted("m1", "user"),
            EngineStreamEvent.PartDelta("m1", "p1", "text", "Rédige la relance"),
            EngineStreamEvent.MessageStarted("m2", "assistant", ran),
            EngineStreamEvent.PartDelta("m2", "p2", "text", "Voici."),
            EngineStreamEvent.Idle,
        )
        every { repository.events("ses_suivie") } returns flowOf()
        val vm = viewModel(sessionId = "ses_suivie")

        assertThat(vm.uiState.value.strongerModel).isEqualTo(opus)
        assertThat(vm.uiState.value.canRedoStronger).isTrue()
        vm.redoStronger()

        coVerify { repository.sendMessage("ses_suivie", REDO_STRONGER_PREFIX + "Rédige la relance", opus.ref, any()) }
        assertThat(vm.uiState.value.effectiveModel).isEqualTo(opus)
    }

    @Test
    fun `at the top of the ladder there is nothing stronger to redo on`() {
        coEvery { repository.history("ses_haute", any()) } returns listOf(
            EngineStreamEvent.MessageStarted("m1", "user"),
            EngineStreamEvent.PartDelta("m1", "p1", "text", "Analyse"),
            EngineStreamEvent.MessageStarted("m2", "assistant", opus.ref),
            EngineStreamEvent.PartDelta("m2", "p2", "text", "Voici."),
            EngineStreamEvent.Idle,
        )
        every { repository.events("ses_haute") } returns flowOf()
        val vm = viewModel(sessionId = "ses_haute")

        assertThat(vm.uiState.value.strongerModel).isNull()
        assertThat(vm.uiState.value.canRedoStronger).isFalse()
        vm.redoStronger()
        coVerify(exactly = 0) { repository.sendMessage(any(), any(), any(), any()) }
    }
}
