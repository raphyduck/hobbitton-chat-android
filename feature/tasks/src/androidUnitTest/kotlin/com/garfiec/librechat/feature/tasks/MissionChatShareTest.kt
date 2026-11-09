package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.engine.AudioTranscriber
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.TranscriptionOutcome
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.feature.tasks.share.PendingShare
import com.garfiec.librechat.feature.tasks.share.ShareIntake
import com.garfiec.librechat.feature.tasks.share.ShareTarget
import com.garfiec.librechat.feature.tasks.util.StagedAttachment
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
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
 * A share from another app (09/11/2026) lands in the composer of the conversation it was aimed
 * at, attachments and text, and nowhere else; never sent on its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MissionChatShareTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val photo = StagedAttachment(id = "p1", mime = "image/jpeg", filename = "recu.jpg", bytes = byteArrayOf(1))
    private val video = StagedAttachment(
        id = "v1", mime = "video/mp4", filename = "fuite.mp4", bytes = ByteArray(0),
        uploadUri = "content://media/video/1", sizeBytes = 48_000_000L,
    )

    private val repository = mockk<EngineMissionRepository>(relaxed = true).also {
        coEvery { it.cachedHistory(any()) } returns null
        coEvery { it.models() } throws IllegalStateException("pas de catalogue")
        coEvery { it.connectors() } throws IllegalStateException("pas de catalogue")
        coEvery { it.history(any(), any()) } returns listOf(EngineStreamEvent.MessageStarted("m1", "user"))
        every { it.events(any()) } returns emptyFlow()
    }
    private val settings = mockk<SettingsDataStore>().also {
        every { it.chatFontSize } returns emptyFlow()
        every { it.localDictation } returns emptyFlow()
    }
    private val intake = ShareIntake()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(sessionId: String?) = MissionChatViewModel(
        sessionId = sessionId,
        repository = repository,
        modelPrices = mockk(relaxed = true),
        settings = settings,
        transcriber = AudioTranscriber { _, _, _ -> TranscriptionOutcome.Heard("") },
        attention = mockk(relaxed = true),
        ioDispatcher = dispatcher,
        shareIntake = intake,
    )

    @Test
    fun `a share aimed at a new conversation fills the blank composer, and is taken once`() {
        val blank = viewModel(sessionId = null)
        val other = viewModel(sessionId = "ses_autre")
        intake.offer(PendingShare(listOf(photo, video), text = "Voilà la fuite"))
        // Unaimed: the chooser is open, nobody takes it yet.
        assertThat(blank.uiState.value.attachments).isEmpty()

        intake.aim(ShareTarget.NewConversation)

        assertThat(blank.uiState.value.attachments).containsExactly(photo, video)
        assertThat(blank.uiState.value.input).isEqualTo("Voilà la fuite")
        assertThat(other.uiState.value.attachments).isEmpty()
        assertThat(other.uiState.value.input).isEmpty()
        assertThat(intake.pending.value).isNull()
    }

    @Test
    fun `a share aimed at an existing conversation lands there, under what was typed`() {
        val target = viewModel(sessionId = "ses_cible")
        target.onInputChange("Regarde :")
        intake.offer(PendingShare(listOf(photo), text = "le reçu"))

        intake.aim(ShareTarget.Conversation("ses_cible"))

        assertThat(target.uiState.value.attachments).containsExactly(photo)
        assertThat(target.uiState.value.input).isEqualTo("Regarde :\nle reçu")
    }

    @Test
    fun `a conversation opened after the pick takes the share that waits for it`() {
        intake.offer(PendingShare(listOf(video)))
        intake.aim(ShareTarget.Conversation("ses_plus_tard"))

        val opened = viewModel(sessionId = "ses_plus_tard")

        assertThat(opened.uiState.value.attachments).containsExactly(video)
        assertThat(intake.pending.value).isNull()
    }
}
