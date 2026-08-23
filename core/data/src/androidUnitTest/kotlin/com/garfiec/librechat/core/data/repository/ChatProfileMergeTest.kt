package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.model.chat.ChatProfile
import com.garfiec.librechat.core.model.request.EphemeralAgent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What the global profile adds to a request, and — more importantly — what it never takes away.
 */
class ChatProfileMergeTest {

    private fun request(
        agentId: String? = null,
        promptPrefix: String? = null,
        ephemeralAgent: EphemeralAgent? = null,
    ) = ChatPayloadBuilder.build(
        text = "bonjour",
        conversationId = null,
        endpoint = "Claude",
        model = "claude-haiku-4-5",
        agentId = agentId,
        ephemeralAgent = ephemeralAgent,
    ).copy(promptPrefix = promptPrefix)

    private val profile = ChatProfile(
        instructions = "Lis index.md avant d'agir.",
        mcpServers = setOf("memoire"),
    )

    @Test
    fun `the profile becomes the run's instructions and tools`() {
        val merged = ChatPayloadBuilder.withProfile(request(), profile)

        assertEquals("Lis index.md avant d'agir.", merged.promptPrefix)
        assertEquals(listOf("memoire"), merged.ephemeralAgent?.mcp)
    }

    @Test
    fun `instructions typed for this message win over the profile`() {
        val merged = ChatPayloadBuilder.withProfile(request(promptPrefix = "Réponds en breton."), profile)

        assertEquals("Réponds en breton.", merged.promptPrefix)
    }

    @Test
    fun `servers asked for on this message are kept, not replaced`() {
        val merged = ChatPayloadBuilder.withProfile(
            request(ephemeralAgent = EphemeralAgent(mcp = listOf("planificateur"))),
            profile,
        )

        assertEquals(listOf("planificateur", "memoire"), merged.ephemeralAgent?.mcp)
    }

    @Test
    fun `a server named twice is sent once`() {
        val merged = ChatPayloadBuilder.withProfile(
            request(ephemeralAgent = EphemeralAgent(mcp = listOf("memoire"))),
            profile,
        )

        assertEquals(listOf("memoire"), merged.ephemeralAgent?.mcp)
    }

    @Test
    fun `other ephemeral flags survive the merge`() {
        val merged = ChatPayloadBuilder.withProfile(
            request(ephemeralAgent = EphemeralAgent(webSearch = true)),
            profile,
        )

        assertEquals(true, merged.ephemeralAgent?.webSearch)
        assertEquals(listOf("memoire"), merged.ephemeralAgent?.mcp)
    }

    @Test
    fun `an agent run is left untouched`() {
        // An agent carries its own instructions and tools. Two systems of instruction in one run,
        // and the loser is whichever the server reads second.
        val original = request(agentId = "agent_42")

        val merged = ChatPayloadBuilder.withProfile(original, profile)

        assertSame(original, merged)
        assertNull(merged.promptPrefix)
    }

    @Test
    fun `a parked profile changes nothing`() {
        val original = request()

        assertSame(original, ChatPayloadBuilder.withProfile(original, profile.copy(enabled = false)))
    }

    @Test
    fun `an empty profile changes nothing`() {
        val original = request()

        assertSame(original, ChatPayloadBuilder.withProfile(original, ChatProfile()))
    }

    @Test
    fun `a blank prompt sends no instruction at all`() {
        // "" as promptPrefix is an empty system message, not the absence of one.
        val merged = ChatPayloadBuilder.withProfile(
            request(),
            ChatProfile(instructions = "   ", mcpServers = setOf("memoire")),
        )

        assertNull(merged.promptPrefix)
        assertTrue(merged.ephemeralAgent?.mcp == listOf("memoire"))
    }
}
