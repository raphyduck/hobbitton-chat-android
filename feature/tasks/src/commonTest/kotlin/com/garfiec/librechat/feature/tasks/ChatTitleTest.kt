package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.core.data.engine.EngineProfile
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import kotlin.test.Test
import kotlin.test.assertEquals

/** A new chat (D-077), as the drawer and the bar name it before the engine does. */
class ChatTitleTest {

    @Test
    fun `a chat is named by its first line`() {
        assertEquals("Quel temps demain ?", chatTitle("\n  Quel temps demain ?  \nEt après-demain ?"))
    }

    @Test
    fun `a long first line is cut`() {
        assertEquals(60, chatTitle("a".repeat(200)).length)
    }

    @Test
    fun `a message without words gives no title`() {
        assertEquals("", chatTitle("   \n "))
    }

    @Test
    fun `a new chat's chip names the chat provider's default`() {
        val default = EngineSelectableModel(providerId = "hobbitton-chat", modelId = "claude-sonnet-5", label = "Claude Sonnet 5")

        val state = MissionChatUiState(profile = EngineProfile.CHAT, defaultModel = default)

        assertEquals(default, state.effectiveModel)
    }

    @Test
    fun `the model picked for the next message outranks the default`() {
        val default = EngineSelectableModel(providerId = "hobbitton-chat", modelId = "claude-sonnet-5", label = "Claude Sonnet 5")
        val picked = EngineSelectableModel(providerId = "hobbitton-chat", modelId = "gpt-5.5", label = "GPT-5.5")

        val state = MissionChatUiState(profile = EngineProfile.CHAT, defaultModel = default, model = picked)

        assertEquals(picked, state.effectiveModel)
    }
}
