package com.garfiec.librechat.core.data.mapper

import com.garfiec.librechat.core.data.db.entity.ConversationEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationMapperNormalizationTest {

    private fun entity(endpoint: String?, endpointType: String? = null) = ConversationEntity(
        conversationId = "c1",
        title = "t",
        user = "u",
        endpoint = endpoint,
        endpointType = endpointType,
        model = null,
        agentId = null,
        isArchived = false,
        tags = "[]",
        iconURL = null,
        greeting = null,
        modelParams = null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun legacyEnumNameConvertsToWireFormat() {
        assertEquals("openAI", entity("OPENAI").toModel().endpoint)
        assertEquals("azureOpenAI", entity("AZURE_OPENAI").toModel().endpoint)
        assertEquals("agents", entity("AGENTS").toModel().endpoint)
    }

    @Test
    fun wireFormatPassesThroughUnchanged() {
        assertEquals("openAI", entity("openAI").toModel().endpoint)
        assertEquals("anthropic", entity("anthropic").toModel().endpoint)
    }

    @Test
    fun customEndpointNamePassesThroughUnchanged() {
        assertEquals("OpenRouter", entity("OpenRouter").toModel().endpoint)
        assertEquals("Deepseek", entity("Deepseek").toModel().endpoint)
    }

    @Test
    fun nullInputReturnsNull() {
        assertNull(entity(null).toModel().endpoint)
        assertNull(entity("openAI", endpointType = null).toModel().endpointType)
    }

    @Test
    fun unrecognizedInputPassesThroughAsIs() {
        assertEquals("SomeUnknownThing", entity("SomeUnknownThing").toModel().endpoint)
    }
}
