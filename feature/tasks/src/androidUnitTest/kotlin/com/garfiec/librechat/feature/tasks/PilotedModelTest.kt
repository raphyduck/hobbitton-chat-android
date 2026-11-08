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
import com.garfiec.librechat.core.model.scheduler.PlatformRoles
import com.garfiec.librechat.core.model.scheduler.RoleModels
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
 * « Piloté » (08/11/2026, D-086 ; « Auto » du 25/10 lisait le premier message par un trieur et
 * choisissait un palier) : une conversation part sur le penseur en service de la plateforme et le
 * garde ; « Demander au savant » met la dernière demande au savant par le penseur.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PilotedModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val deepseek = EngineSelectableModel("hobbitton-chat", "deepseek-v4-pro", "DeepSeek V4 Pro")
    private val opus = EngineSelectableModel("hobbitton-chat", "claude-opus-5-5", "Claude Opus 5.5")
    private val fable = EngineSelectableModel("hobbitton-chat", "claude-fable-5-1", "Claude Fable 5.1")
    private val roles = PlatformRoles(
        roles = mapOf(
            PlatformRoles.THINKER to RoleModels("claude-opus-5-5", listOf("claude-opus-5-5", "gpt-6.1-sol")),
            PlatformRoles.SAVANT to RoleModels("claude-fable-5-1", listOf("claude-fable-5-1")),
            PlatformRoles.SCOUT to RoleModels("deepseek-flash", listOf("deepseek-flash")),
        ),
        pilots = PlatformRoles.PILOTS,
    )

    private val repository = mockk<EngineMissionRepository>(relaxed = true).also {
        // A relaxed mock would answer a mock tail: no conversation was kept unless a test says so.
        coEvery { it.cachedHistory(any()) } returns null
        coEvery { it.models() } returns EngineModelChoice(
            models = listOf(deepseek, opus, fable),
            preselected = EngineSelectableModel.AUTO,
            engineDefault = opus,
        )
        coEvery { it.connectors() } returns ConnectorCatalogue(connecteurs = emptyMap())
        coEvery { it.startChat(any(), any(), any(), any()) } returns "ses_nouvelle"
    }
    private val scheduler = mockk<SchedulerRepository>(relaxed = true).also {
        coEvery { it.roles() } returns roles
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
    fun `a new conversation on Piloté starts on the thinker in service, and the chip still reads Piloté`() {
        val vm = viewModel()
        assertThat(vm.uiState.value.effectiveModel).isEqualTo(EngineSelectableModel.AUTO)
        assertThat(vm.uiState.value.showsPiloted).isTrue()

        vm.onInputChange("Analyse ce devis")
        vm.send()

        coVerify { repository.startChat(text = "Analyse ce devis", connectors = any(), model = opus.ref, files = any()) }
        // The conversation now runs on the thinker, which is what « Piloté » means: the chip does
        // not switch to the model's name.
        assertThat(vm.uiState.value.rememberedModel).isEqualTo(opus.ref)
        assertThat(vm.uiState.value.showsPiloted).isTrue()
    }

    @Test
    fun `without a roles table the message goes without a model, to the engine's own default`() {
        coEvery { scheduler.roles() } returns null
        val vm = viewModel()

        vm.onInputChange("Bonjour")
        vm.send()

        coVerify { repository.startChat(text = "Bonjour", connectors = any(), model = null, files = any()) }
        assertThat(vm.uiState.value.canAskSavant).isFalse()
    }

    @Test
    fun `a conversation that already ran keeps its model`() {
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

        coVerify { repository.sendMessage("ses_suivie", "Et ensuite ?", ran, any()) }
        assertThat(vm.uiState.value.showsPiloted).isFalse()
    }

    @Test
    fun `a conversation that ran on the engine's fallback nobody chose follows Piloté`() {
        // 27/10/2026, Raphaël: « je n'ai jamais choisi Sonnet ». A turn sent without a model ran
        // on the engine's default; the conversation is not held to it.
        val sonnet = EngineSelectableModel("hobbitton-chat", "claude-sonnet-5-5", "Claude Sonnet 5.5")
        coEvery { repository.models() } returns EngineModelChoice(
            models = listOf(deepseek, opus, sonnet),
            preselected = EngineSelectableModel.AUTO,
            engineDefault = sonnet,
        )
        coEvery { repository.history("ses_colis", any()) } returns listOf(
            EngineStreamEvent.MessageStarted("m1", "user"),
            EngineStreamEvent.MessageStarted("m2", "assistant", sonnet.ref),
            EngineStreamEvent.Idle,
        )
        every { repository.events("ses_colis") } returns flowOf()
        val vm = viewModel(sessionId = "ses_colis")

        assertThat(vm.uiState.value.effectiveModel).isEqualTo(EngineSelectableModel.AUTO)
        vm.onInputChange("Et le point relais ?")
        vm.send()

        coVerify { repository.sendMessage("ses_colis", "Et le point relais ?", opus.ref, any()) }
    }

    @Test
    fun `the savant is offered in the picker and picking it runs the conversation on it`() {
        val vm = viewModel()
        assertThat(vm.uiState.value.savantModel).isEqualTo(fable)
        vm.selectModel(fable)
        assertThat(vm.uiState.value.showsPiloted).isFalse()

        vm.onInputChange("Faut-il vendre la SCPI ?")
        vm.send()

        coVerify { repository.startChat(text = "Faut-il vendre la SCPI ?", connectors = any(), model = fable.ref, files = any()) }
    }

    @Test
    fun `ask the savant puts the last request to the savant through the conversation's model`() {
        val ran = EngineModelRef("hobbitton-chat", "claude-opus-5-5")
        coEvery { repository.history("ses_suivie", any()) } returns listOf(
            EngineStreamEvent.MessageStarted("m1", "user"),
            EngineStreamEvent.PartDelta("m1", "p1", "text", "Rédige la relance"),
            EngineStreamEvent.MessageStarted("m2", "assistant", ran),
            EngineStreamEvent.PartDelta("m2", "p2", "text", "Voici."),
            EngineStreamEvent.Idle,
        )
        every { repository.events("ses_suivie") } returns flowOf()
        val vm = viewModel(sessionId = "ses_suivie")

        assertThat(vm.uiState.value.canAskSavant).isTrue()
        vm.askSavant()

        coVerify { repository.sendMessage("ses_suivie", ASK_SAGE_PREFIX + "Rédige la relance", ran, any()) }
    }
}
