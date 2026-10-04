package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.engine.AttentionSignals
import com.garfiec.librechat.core.data.engine.AudioTranscriber
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineProfile
import com.garfiec.librechat.core.data.engine.TranscriptionOutcome
import com.garfiec.librechat.core.model.engine.EngineQuestionInfo
import com.garfiec.librechat.core.model.engine.EngineQuestionOption
import com.garfiec.librechat.core.model.engine.EngineQuestionRequest
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * La question de l'agent dans la conversation (03/10/2026) : lue à l'ouverture comme sur le flux,
 * répondue ou ignorée depuis le formulaire, et une réponse prête signalée quand personne ne la voit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MissionQuestionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val feed = MutableSharedFlow<EngineStreamEvent>()

    private val question = EngineQuestionRequest(
        id = "que_1",
        sessionId = "ses_1",
        questions = listOf(
            EngineQuestionInfo(
                question = "Quel compte ?",
                header = "Compte",
                options = listOf(EngineQuestionOption("Qonto", "pro"), EngineQuestionOption("CMB", "privée")),
            ),
        ),
    )

    private val repository = mockk<EngineMissionRepository>(relaxed = true).also {
        coEvery { it.models(any()) } throws IllegalStateException("pas de modèles")
        coEvery { it.history(any()) } returns emptyList()
        coEvery { it.pendingQuestions("ses_1") } returns listOf(question)
        every { it.events(any()) } returns feed
    }
    private val attention = mockk<AttentionSignals>(relaxed = true)
    private val settings = mockk<SettingsDataStore>().also { every { it.chatFontSize } returns emptyFlow() }

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
        positions = mockk(relaxed = true),
        transcriber = AudioTranscriber { _, _, _ -> TranscriptionOutcome.Heard("") },
        attention = attention,
        ioDispatcher = dispatcher,
        profile = EngineProfile.TASK,
    )

    @Test
    fun `a question already waiting shows at opening and its answer resumes the turn`() {
        val vm = viewModel()
        assertThat(vm.uiState.value.pendingQuestion).isEqualTo(question)

        // Nothing to send until something is picked.
        vm.answerQuestion()
        coVerify(exactly = 0) { repository.answerQuestion(any(), any()) }

        vm.pickQuestionOption(0, "CMB")
        vm.answerQuestion()

        coVerify { repository.answerQuestion("que_1", listOf(listOf("CMB"))) }
        assertThat(vm.uiState.value.pendingQuestion).isNull()
        assertThat(vm.uiState.value.answeringQuestion).isFalse()
        verify { attention.questionClosed("que_1") }
    }

    @Test
    fun `a failed answer keeps the form as it was filled`() {
        coEvery { repository.answerQuestion(any(), any()) } throws IllegalStateException("hors ligne")
        val vm = viewModel()
        vm.typeQuestionAnswer(0, "Le compte joint")

        vm.answerQuestion()

        assertThat(vm.uiState.value.pendingQuestion).isEqualTo(question)
        assertThat(vm.uiState.value.questionDraftFor(question).answers()).containsExactly(listOf("Le compte joint"))
        assertThat(vm.uiState.value.questionError).isNotNull()
    }

    @Test
    fun `dismissing lets the agent go on without the answer`() {
        val vm = viewModel()

        vm.dismissQuestion()

        coVerify { repository.dismissQuestion("que_1") }
        assertThat(vm.uiState.value.pendingQuestion).isNull()
    }

    @Test
    fun `a reply that streamed in live is signalled when it ends`() = kotlinx.coroutines.test.runTest(dispatcher) {
        viewModel()

        feed.emit(EngineStreamEvent.PartDelta("msg_a", "prt_1", "text", "Voilà"))
        feed.emit(EngineStreamEvent.Idle)
        // A second idle, with nothing seen start, rings nothing more.
        feed.emit(EngineStreamEvent.Idle)

        coVerify(exactly = 1) { attention.replyReady("ses_1", any()) }
    }

    @Test
    fun `the screen tells the signals when it is in front of the person`() {
        val vm = viewModel()
        vm.onVisible()
        vm.onHidden()
        verify { attention.enter("ses_1") }
        verify { attention.leave("ses_1") }
    }
}
