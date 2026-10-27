package com.garfiec.librechat.shared.engine.voice

import com.garfiec.librechat.core.model.scheduler.VoiceRoom
import com.garfiec.librechat.core.network.engine.EngineHttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EngineVoiceViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun main() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun reset() = Dispatchers.resetMain()

    private class FakeLink : VoiceLink {
        override val state = MutableStateFlow<VoiceLinkState>(VoiceLinkState.Idle)
        var joined: Pair<String, String>? = null
        var left = 0
        override suspend fun connect(url: String, token: String) {
            joined = url to token
            state.value = VoiceLinkState.Connecting
            state.value = VoiceLinkState.Live(agentState = "listening")
        }
        override fun disconnect() {
            left += 1
            state.value = VoiceLinkState.Idle
        }
    }

    @Test
    fun `opening a room joins it and the screen follows the line`() = runTest(dispatcher) {
        val link = FakeLink()
        val viewModel = EngineVoiceViewModel(rooms = { VoiceRoom("wss://lk.test", "app-1", "jwt") }, link = link)

        viewModel.start()
        advanceUntilIdle()
        assertEquals("wss://lk.test" to "jwt", link.joined)
        assertEquals(EngineVoiceUiState.Phase.LIVE, viewModel.state.value.phase)
        assertEquals("listening", viewModel.state.value.agentState)

        link.state.value = VoiceLinkState.Live(agentState = "speaking", agentText = "Deux choses t'attendent.")
        advanceUntilIdle()
        assertEquals("speaking", viewModel.state.value.agentState)
        assertEquals("Deux choses t'attendent.", viewModel.state.value.agentText)

        viewModel.stop()
        advanceUntilIdle()
        assertEquals(1, link.left)
        assertEquals(EngineVoiceUiState.Phase.ENDED, viewModel.state.value.phase)
    }

    @Test
    fun `a scheduler without LiveKit settings is said as such`() = runTest(dispatcher) {
        val link = FakeLink()
        val viewModel = EngineVoiceViewModel(rooms = { throw EngineHttpException(503, "POST", "/vocal") }, link = link)

        viewModel.start()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.serverUnconfigured)
        assertEquals(EngineVoiceUiState.Phase.IDLE, viewModel.state.value.phase)
        assertEquals(null, link.joined)
    }

    @Test
    fun `without the engine graph nothing starts`() = runTest(dispatcher) {
        val viewModel = EngineVoiceViewModel(rooms = null, link = null)
        viewModel.start()
        assertTrue(viewModel.state.value.serverUnconfigured)
    }

}
