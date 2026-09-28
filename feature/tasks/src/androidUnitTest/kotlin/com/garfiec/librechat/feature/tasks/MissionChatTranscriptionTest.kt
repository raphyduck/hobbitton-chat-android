package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.engine.AudioTranscriber
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.EngineProfile
import com.garfiec.librechat.core.data.engine.TranscriptionFailure
import com.garfiec.librechat.core.data.engine.TranscriptionOutcome
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
 * La dictée et les fichiers audio, de l'écran de conversation jusqu'au transcripteur — sur une
 * conversation comme sur une tâche, puisque les deux passent par le même écran depuis D-077.
 *
 * Le moteur est un double : ce qu'on vérifie, c'est ce qui atterrit dans le composeur, ce qui part
 * avec le message, et surtout ce qui ne part pas tout seul.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MissionChatTranscriptionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val audio = byteArrayOf(1, 2, 3)

    private val repository = mockk<EngineMissionRepository>(relaxed = true).also {
        // No catalogue: the composer's pills are not what these tests are about.
        coEvery { it.models(any()) } throws IllegalStateException("pas de catalogue")
        coEvery { it.connectors() } throws IllegalStateException("pas de catalogue")
        coEvery { it.startChat(any(), any(), any()) } returns "ses_nouvelle"
    }
    private val settings = mockk<SettingsDataStore>().also { every { it.chatFontSize } returns emptyFlow() }

    /** What the transcriber was handed, in order: mime and file name. */
    private val sent = mutableListOf<Pair<String, String>>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun transcriber(outcome: TranscriptionOutcome) = AudioTranscriber { _, mime, filename ->
        sent += mime to filename
        outcome
    }

    private fun viewModel(
        outcome: TranscriptionOutcome,
        profile: EngineProfile = EngineProfile.CHAT,
    ) = MissionChatViewModel(
        sessionId = null,
        repository = repository,
        modelPrices = mockk(relaxed = true),
        settings = settings,
        positions = mockk(relaxed = true),
        transcriber = transcriber(outcome),
        ioDispatcher = dispatcher,
        profile = profile,
    )

    @Test
    fun `a dictation lands in the composer and is not sent`() {
        val vm = viewModel(TranscriptionOutcome.Heard(" le point sur la boîte mail "))
        vm.onInputChange("Fais")

        vm.transcribeAudio(audio, "audio/ogg", "dictee.ogg")

        assertThat(vm.uiState.value.input).isEqualTo("Fais le point sur la boîte mail")
        assertThat(vm.uiState.value.transcribing).isFalse()
        assertThat(sent).containsExactly("audio/ogg" to "dictee.ogg")
        coVerify(exactly = 0) { repository.startChat(any(), any(), any()) }
        coVerify(exactly = 0) { repository.sendMessage(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a failed dictation on a task says why and keeps the text`() {
        val vm = viewModel(
            TranscriptionOutcome.Failed(TranscriptionFailure.UNAVAILABLE, "transcription indisponible"),
            profile = EngineProfile.TASK,
        )
        vm.onInputChange("déjà tapé")

        vm.transcribeAudio(audio, "audio/ogg", "dictee.ogg")

        val state = vm.uiState.value
        assertThat(state.input).isEqualTo("déjà tapé")
        assertThat(state.transcriptionError?.failure).isEqualTo(TranscriptionFailure.UNAVAILABLE)
        assertThat(state.transcriptionError?.reason).isEqualTo("transcription indisponible")
        assertThat(state.transcribing).isFalse()

        vm.dismissTranscriptionError()
        assertThat(vm.uiState.value.transcriptionError).isNull()
    }

    @Test
    fun `an audio file leaves with the message as its transcription`() {
        val vm = viewModel(TranscriptionOutcome.Heard("le texte entendu"))

        vm.attachAudio(audio, "audio/mp4", "memo.m4a")
        assertThat(vm.uiState.value.audioNotes.map { it.filename }).containsExactly("memo.m4a")
        assertThat(vm.uiState.value.input).isEmpty()

        vm.send()

        coVerify {
            repository.startChat(
                text = match { it.contains("memo.m4a") && it.contains("> le texte entendu") },
                model = any(),
                files = emptyList(),
            )
        }
        assertThat(vm.uiState.value.audioNotes).isEmpty()
        assertThat(vm.uiState.value.started?.sessionId).isEqualTo("ses_nouvelle")
    }
}
