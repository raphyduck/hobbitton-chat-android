package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.feature.tasks.util.MissionChatState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

        val state = MissionChatUiState(defaultModel = default)

        assertEquals(default, state.effectiveModel)
    }

    @Test
    fun `the model picked for the next message outranks the default`() {
        val default = EngineSelectableModel(providerId = "hobbitton-chat", modelId = "claude-sonnet-5", label = "Claude Sonnet 5")
        val picked = EngineSelectableModel(providerId = "hobbitton-chat", modelId = "gpt-5.5", label = "GPT-5.5")

        val state = MissionChatUiState(defaultModel = default, model = picked)

        assertEquals(picked, state.effectiveModel)
    }

    @Test
    fun `a piloted conversation stays piloted whatever its last turn ran on`() {
        // 08/11/2026 evening, Raphaël: « une fois qu'une session est en mode pilote, elle doit le
        // rester à chaque nouvelle réponse ». The pick kept is « Piloté » itself; the model of the
        // last turn (a reminder's, the penseur of that day) never decides.
        val default = EngineSelectableModel(providerId = "hobbitton-chat", modelId = "claude-opus-5-5", label = "Opus")
        val ran = EngineModelRef(providerId = "hobbitton-chat", modelId = "deepseek-flash")
        val state = MissionChatUiState(
            defaultModel = default,
            rememberedModel = EngineSelectableModel.AUTO.ref,
            chat = MissionChatState(model = ran),
        )

        assertEquals(EngineSelectableModel.AUTO, state.effectiveModel)
        assertTrue(state.showsPiloted)
        // A model picked for the conversation is kept as such.
        val kept = MissionChatUiState(defaultModel = default, rememberedModel = ran, chat = MissionChatState(model = default.ref))
        assertEquals(ran, kept.effectiveModel?.ref)
        // Nothing recorded: the person's default, not the last turn's model.
        val fresh = MissionChatUiState(defaultModel = default, chat = MissionChatState(model = ran))
        assertEquals(default, fresh.effectiveModel)
    }
}
