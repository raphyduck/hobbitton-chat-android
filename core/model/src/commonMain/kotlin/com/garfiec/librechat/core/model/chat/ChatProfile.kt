package com.garfiec.librechat.core.model.chat

/**
 * What every conversation starts with, whatever model is chosen.
 *
 * LibreChat has no notion of a profile that spans models: instructions and tools are attached to an
 * agent, and an agent pins one model. Configuring the same thing nine times — once per model in the
 * catalogue — is nine chances to configure it eight times.
 *
 * So the profile lives here and rides on **every** send: [instructions] as the run's `promptPrefix`,
 * [mcpServers] as its `ephemeralAgent.mcp`. The server turns both into an ephemeral agent
 * (`instructions: promptPrefix || ''`, `mcpServers: new Set(ephemeralAgent?.mcp)`) — read in its
 * source, not assumed.
 *
 * @param enabled false parks the profile without erasing it, for the day a raw model is wanted.
 * @param instructions the system prompt. Blank sends nothing rather than an empty instruction.
 * @param mcpServers server names as the server itself spells them (`memoire`, `planificateur`) —
 *   they come from `GET /api/mcp/servers`, never typed from memory.
 */
data class ChatProfile(
    val enabled: Boolean = true,
    val instructions: String = "",
    val mcpServers: Set<String> = emptySet(),
) {
    /** Nothing to add to a request: the merge can then skip every allocation and stay a no-op. */
    val isEmpty: Boolean get() = !enabled || (instructions.isBlank() && mcpServers.isEmpty())

    companion object {
        val NONE = ChatProfile(enabled = false)
    }
}
