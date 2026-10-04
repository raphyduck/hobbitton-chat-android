package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.engine.AudioTranscriber
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineProfile
import com.garfiec.librechat.core.data.engine.TranscriptionOutcome
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.core.model.scheduler.ConnectorGrant
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
 * Une nouvelle tâche s'ouvre sur l'écran d'une tâche existante (02/10/2026) : le premier message,
 * avec ses fichiers et les connecteurs cochés sur la puce, est ce qui la crée.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewTaskConversationTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val repository = mockk<EngineMissionRepository>(relaxed = true).also {
        coEvery { it.models(any()) } throws IllegalStateException("pas de modèles")
        coEvery { it.connectors() } returns ConnectorCatalogue(
            connecteurs = mapOf(
                "memoire" to ConnectorGrant(tickedByDefault = true),
                "nas" to ConnectorGrant(direct = false),
                "shell" to ConnectorGrant(),
            ),
        )
        coEvery { it.launch(any(), any(), any(), any()) } returns "ses_tache"
    }
    private val settings = mockk<SettingsDataStore>().also { every { it.chatFontSize } returns emptyFlow() }

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
        positions = mockk(relaxed = true),
        transcriber = AudioTranscriber { _, _, _ -> TranscriptionOutcome.Heard("") },
        attention = mockk(relaxed = true),
        ioDispatcher = dispatcher,
        profile = EngineProfile.TASK,
    )

    @Test
    fun `a new task opens with the default ticks, and its first message creates it`() {
        val vm = viewModel()
        assertThat(vm.uiState.value.isNew).isTrue()
        assertThat(vm.uiState.value.enabledConnectors).containsExactly("memoire", "nas")

        // Ticked before it exists: kept on screen, nothing sent to an engine session.
        vm.toggleConnector("shell")
        coVerify(exactly = 0) { repository.setConnectors(any(), any()) }

        val photo = StagedAttachment(id = "p1", mime = "image/jpeg", filename = "recu.jpg", bytes = byteArrayOf(1))
        vm.addAttachments(listOf(photo))
        vm.onInputChange("Classe ce reçu")
        vm.send()

        coVerify {
            repository.launch(
                objective = "Classe ce reçu",
                connectors = match { it.toSet() == setOf("memoire", "nas", "shell") },
                model = null,
                files = match { files -> files.single().filename == "recu.jpg" },
            )
        }
        coVerify(exactly = 0) { repository.startChat(any(), any(), any()) }
        assertThat(vm.uiState.value.started?.sessionId).isEqualTo("ses_tache")
        assertThat(vm.uiState.value.attachments).isEmpty()
    }

    @Test
    fun `a reopened task sends the model its last turn ran on, not the agent's default`() {
        // Picked on another day, then the app restarted: the pick is gone, the transcript is not.
        val deepseek = EngineModelRef("hobbitton-gateway", "deepseek-v4-pro")
        coEvery { repository.history("ses_suivie") } returns listOf(
            EngineStreamEvent.MessageStarted("m1", "user"),
            EngineStreamEvent.MessageStarted("m2", "assistant", deepseek),
        )
        every { repository.events("ses_suivie") } returns flowOf()
        val vm = viewModel(sessionId = "ses_suivie")

        vm.onInputChange("Envoie")
        vm.send()

        coVerify { repository.sendMessage("ses_suivie", "Envoie", deepseek, any(), EngineProfile.TASK) }
    }
}
