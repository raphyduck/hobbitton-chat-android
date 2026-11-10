package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.engine.AudioTranscriber
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineModelChoice
import com.garfiec.librechat.core.data.engine.PILOTED_MODE
import com.garfiec.librechat.core.data.engine.SessionScopeRead
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
 * choisissait un palier) : une conversation part sur le pilote en service de la plateforme
 * (l'éclaireur depuis le 10/11/2026) et le garde, sans qu'Eva nomme de modèle depuis le
 * 10/11/2026 (le serveur définit les modes : le moteur prend celui de l'agent `chat`) ; « Demander au savant » met la dernière demande
 * au savant par le pilote.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PilotedModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val deepseek = EngineSelectableModel("hobbitton-chat", "deepseek-v4-pro", "DeepSeek V4 Pro")
    private val opus = EngineSelectableModel("hobbitton-chat", "claude-opus-5-5", "Claude Opus 5.5")
    private val fable = EngineSelectableModel("hobbitton-chat", "claude-fable-5-1", "Claude Fable 5.1")
    private val flash = EngineSelectableModel("hobbitton-chat", "deepseek-flash", "DeepSeek Flash")
    private val roles = PlatformRoles(
        roles = mapOf(
            PlatformRoles.THINKER to RoleModels("claude-opus-5-5", listOf("claude-opus-5-5", "gpt-6.1-sol")),
            PlatformRoles.SAVANT to RoleModels("claude-fable-5-1", listOf("claude-fable-5-1")),
            PlatformRoles.SCOUT to RoleModels("deepseek-flash", listOf("deepseek-flash")),
        ),
    )

    private val repository = mockk<EngineMissionRepository>(relaxed = true).also {
        // A relaxed mock would answer a mock tail: no conversation was kept unless a test says so.
        coEvery { it.cachedHistory(any()) } returns null
        coEvery { it.models() } returns EngineModelChoice(
            models = listOf(deepseek, opus, fable, flash),
            preselected = EngineSelectableModel.AUTO,
            engineDefault = opus,
        )
        coEvery { it.connectors() } returns ConnectorCatalogue(connecteurs = emptyMap())
        coEvery { it.startChat(any(), any(), any(), any(), any()) } returns "ses_nouvelle"
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
    fun `a new conversation on Piloté goes without a model, in the pilot's mode, and the chip reads Piloté`() {
        val vm = viewModel()
        assertThat(vm.uiState.value.effectiveModel).isEqualTo(EngineSelectableModel.AUTO)
        assertThat(vm.uiState.value.showsPiloted).isTrue()

        vm.onInputChange("Analyse ce devis")
        vm.send()

        // 10/11/2026 (le serveur définit les modes) : no model, the engine runs the chat agent's own,
        // the pilot in service; the scheduler writes the rules of the pilot's mode.
        coVerify {
            repository.startChat(text = "Analyse ce devis", connectors = any(), model = null, files = any(), mode = PILOTED_MODE)
        }
        // The chip does not switch to the model's name.
        assertThat(vm.uiState.value.rememberedModel).isEqualTo(EngineSelectableModel.AUTO.ref)
        assertThat(vm.uiState.value.showsPiloted).isTrue()
    }

    @Test
    fun `without a roles table the message goes without a model too, and no savant is offered`() {
        coEvery { scheduler.roles() } returns null
        val vm = viewModel()

        vm.onInputChange("Bonjour")
        vm.send()

        coVerify { repository.startChat(text = "Bonjour", connectors = any(), model = null, files = any(), mode = PILOTED_MODE) }
        assertThat(vm.uiState.value.canAskSavant).isFalse()
    }

    @Test
    fun `a model picked here stays the conversation's, send after send, and the last turn never decides`() {
        // 08/11/2026 evening, Raphaël: « une fois qu'une session est en mode pilote, elle doit le
        // rester à chaque nouvelle réponse, jusqu'à ce que je décide de changer ». Nothing picked
        // here: Piloté, whatever the last turn ran on; a model picked here is kept the same way.
        coEvery { repository.history("ses_suivie", any()) } returns listOf(
            EngineStreamEvent.MessageStarted("m1", "user"),
            EngineStreamEvent.MessageStarted("m2", "assistant", deepseek.ref),
        )
        every { repository.events("ses_suivie") } returns flowOf()
        val vm = viewModel(sessionId = "ses_suivie")
        assertThat(vm.uiState.value.effectiveModel).isEqualTo(EngineSelectableModel.AUTO)
        assertThat(vm.uiState.value.showsPiloted).isTrue()

        vm.onInputChange("Et ensuite ?")
        vm.send()
        coVerify { repository.sendMessage("ses_suivie", "Et ensuite ?", null, any()) }

        vm.selectModel(deepseek)
        vm.onInputChange("Sur DeepSeek")
        vm.send()
        vm.onInputChange("Encore")
        vm.send()

        coVerify { repository.sendMessage("ses_suivie", "Sur DeepSeek", deepseek.ref, any()) }
        coVerify { repository.sendMessage("ses_suivie", "Encore", deepseek.ref, any()) }
        assertThat(vm.uiState.value.rememberedModel).isEqualTo(deepseek.ref)
        assertThat(vm.uiState.value.showsPiloted).isFalse()
    }

    @Test
    fun `a conversation that ran on the engine's fallback nobody chose follows Piloté`() {
        // 27/10/2026, Raphaël: « je n'ai jamais choisi Sonnet ». A turn sent without a model ran
        // on the engine's default; the conversation is not held to it.
        val sonnet = EngineSelectableModel("hobbitton-chat", "claude-sonnet-5-5", "Claude Sonnet 5.5")
        coEvery { repository.models() } returns EngineModelChoice(
            models = listOf(deepseek, opus, sonnet, flash),
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

        coVerify { repository.sendMessage("ses_colis", "Et le point relais ?", null, any()) }
    }

    @Test
    fun `the savant is offered in the picker and picking it runs the conversation on it`() {
        val vm = viewModel()
        assertThat(vm.uiState.value.savantModel).isEqualTo(fable)
        vm.selectModel(fable)
        assertThat(vm.uiState.value.showsPiloted).isFalse()

        vm.onInputChange("Faut-il vendre la SCPI ?")
        vm.send()

        coVerify {
            repository.startChat(
                text = "Faut-il vendre la SCPI ?", connectors = any(), model = fable.ref, files = any(),
                mode = "hobbitton-chat/${fable.ref.modelId}",
            )
        }
    }

    @Test
    fun `ask the savant puts the last request to the savant through the pilot of a piloted conversation`() {
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

        // Piloté: the request goes without a model, to the pilot in service, whatever the last turn ran on.
        coVerify { repository.sendMessage("ses_suivie", ASK_SAGE_PREFIX + "Rédige la relance", null, any()) }
    }
    @Test
    fun `a development session keeps its agent and its coder when the person answers in it`() {
        // 10/11/2026, Raphaël: an answer in a development session the scheduler opened went to
        // the pilot on `chat`, and the session was no longer one. The scheduler says its agent and
        // its model; every turn goes on them, with nothing lifted nor rewritten.
        val coder = EngineModelRef("hobbitton-chat", "kimi-k2.7-code")
        coEvery { repository.history("ses_dev", any()) } returns listOf(
            EngineStreamEvent.MessageStarted("m1", "user"),
        )
        every { repository.events("ses_dev") } returns flowOf()
        coEvery { repository.sessionScope("ses_dev") } returns SessionScopeRead(
            connectors = emptySet(), level = "titulaire", mode = "developpement",
            agent = "code-general", model = coder,
        )
        val vm = viewModel(sessionId = "ses_dev")
        assertThat(vm.uiState.value.sessionAgent).isEqualTo("code-general")
        assertThat(vm.uiState.value.showsPiloted).isFalse()

        vm.onInputChange("Continue")
        vm.send()

        coVerify { repository.sendMessage("ses_dev", "Continue", coder, any(), "code-general") }
        coVerify(exactly = 0) { repository.setConnectors(any(), any(), any()) }
    }
}
