package com.garfiec.librechat.core.data.endpoint

import com.garfiec.librechat.core.model.EndpointConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EndpointClassifierTest {

    @Test
    fun customEndpointWithoutConfig_returnsCustom() {
        val dispatch = EndpointClassifier.classify("OpenRouter", emptyMap())
        assertEquals("custom", dispatch.endpointType)
        assertNull(dispatch.key)
        assertEquals("OpenRouter", dispatch.modelDisplayLabel)
    }

    @Test
    fun builtInEndpointWithColdStartConfig_usesFallback() {
        val dispatch = EndpointClassifier.classify("openAI", emptyMap())
        assertEquals("openAI", dispatch.endpointType)
        assertNull(dispatch.key)
    }

    @Test
    fun userProvidedEndpointSendsKey() {
        val configs = mapOf(
            "OpenRouter" to EndpointConfig(
                type = "custom",
                userProvide = true,
                modelDisplayLabel = "OpenRouter",
            ),
        )
        val dispatch = EndpointClassifier.classify("OpenRouter", configs)
        assertEquals("never", dispatch.key)
        assertEquals("custom", dispatch.endpointType)
    }

    @Test
    fun envKeyEndpointOmitsKey() {
        val configs = mapOf(
            "anthropic" to EndpointConfig(
                type = "anthropic",
                userProvide = false,
            ),
        )
        val dispatch = EndpointClassifier.classify("anthropic", configs)
        assertNull(dispatch.key)
    }

    @Test
    fun customOverridingBuiltInNamePrefersConfigType() {
        val configs = mapOf(
            "openAI" to EndpointConfig(
                type = "custom",
                userProvide = true,
            ),
        )
        val dispatch = EndpointClassifier.classify("openAI", configs)
        assertEquals("custom", dispatch.endpointType)
    }

    @Test
    fun modelDisplayLabelFallsBackToEndpointName() {
        val configs = mapOf(
            "OpenRouter" to EndpointConfig(
                type = "custom",
                modelDisplayLabel = null,
            ),
        )
        val dispatch = EndpointClassifier.classify("OpenRouter", configs)
        assertEquals("OpenRouter", dispatch.modelDisplayLabel)
    }
}
