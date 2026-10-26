package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.engine.EngineMissionRepository
import com.garfiec.librechat.core.data.engine.SessionScopeRead
import com.garfiec.librechat.core.data.engine.TITULAR_LEVEL
import com.garfiec.librechat.core.data.engine.TranscriptionOutcome
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * The person's first message in a session that is not at the titulaire's level lifts it there
 * (D-083; 24/10/2026: « Go ssh » answered in a scheduled run was refused by the annuaire because
 * the run still stood at the mission's level).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MissionChatLiftTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val repository = mockk<EngineMissionRepository>(relaxed = true).also {
        coEvery { it.models() } throws IllegalStateException("pas de catalogue")
        // A catalogue with nothing in it: enough for the scope, and its level, to be read.
        coEvery { it.connectors() } returns ConnectorCatalogue()
        coEvery { it.history(any(), any()) } returns emptyList()
        every { it.events(any()) } returns emptyFlow()
        coEvery { it.sendMessage(any(), any(), any(), any()) } returns emptyList()
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

    private fun viewModel(level: String?): MissionChatViewModel {
        coEvery { repository.sessionScope("ses_run") } returns SessionScopeRead(emptySet(), level)
        return MissionChatViewModel(
            sessionId = "ses_run",
            repository = repository,
            modelPrices = mockk(relaxed = true),
            settings = settings,
            transcriber = { _, _, _ -> TranscriptionOutcome.Heard("") },
            attention = mockk(relaxed = true),
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun `a run at the mission's level is lifted once before the first message`() {
        val vm = viewModel(level = "mission")
        vm.onInputChange("Go ssh")
        vm.send()
        vm.onInputChange("et ensuite")
        vm.send()

        coVerifyOrder {
            repository.setConnectors("ses_run", any())
            repository.sendMessage("ses_run", "Go ssh", any(), any())
            repository.sendMessage("ses_run", "et ensuite", any(), any())
        }
        coVerify(exactly = 1) { repository.setConnectors("ses_run", any()) }
    }

    @Test
    fun `a conversation already at the titulaire's level is left as it is`() {
        val vm = viewModel(level = TITULAR_LEVEL)
        vm.onInputChange("bonjour")
        vm.send()

        coVerify(exactly = 0) { repository.setConnectors(any(), any()) }
        coVerify { repository.sendMessage("ses_run", "bonjour", any(), any()) }
    }

    @Test
    fun `an unknown level is lifted too, the cheap side of the doubt`() {
        val vm = viewModel(level = null)
        vm.onInputChange("bonjour")
        vm.send()

        coVerify(exactly = 1) { repository.setConnectors("ses_run", any()) }
    }

    @Test
    fun `connectors that could not be read lift the level alone and never strip the session`() {
        // 26/10/2026 : la lecture du périmètre avait échoué, l'envoi a réécrit les règles avec une
        // liste vide et la session a perdu tous ses outils directs.
        coEvery { repository.sessionScope("ses_run") } throws IllegalStateException("planificateur injoignable")
        val vm = MissionChatViewModel(
            sessionId = "ses_run",
            repository = repository,
            modelPrices = mockk(relaxed = true),
            settings = settings,
            transcriber = { _, _, _ -> TranscriptionOutcome.Heard("") },
            attention = mockk(relaxed = true),
            ioDispatcher = dispatcher,
        )
        assertThat(vm.uiState.value.connectorsError).isNotNull()
        assertThat(vm.uiState.value.enabledConnectors).isNull()

        vm.onInputChange("reprends les annonces")
        vm.send()

        coVerify(exactly = 0) { repository.setConnectors(any(), any()) }
        coVerify(exactly = 1) { repository.liftToTitular("ses_run") }
        coVerify { repository.sendMessage("ses_run", "reprends les annonces", any(), any()) }
    }
}
