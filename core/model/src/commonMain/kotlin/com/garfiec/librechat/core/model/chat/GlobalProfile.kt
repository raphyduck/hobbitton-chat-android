package com.garfiec.librechat.core.model.chat

/**
 * What every conversation and every mission starts with, whatever model is chosen and whichever
 * surface it runs on. **The only profile in this application** — there is no second one to keep in
 * step with, which is the point.
 *
 * Neither backend offers this by itself, and for the same shape of reason. LibreChat attaches
 * instructions and tools to an agent, and an agent pins one model: configuring the same thing once
 * per model in the catalogue is nine chances to configure it eight times. The Agent engine attaches
 * a charter to a *profile*, one per métier, decided server-side and out of reach of the phone.
 * Neither has a place for « what I want of everything ».
 *
 * So it lives here and rides on **every** send, on both surfaces:
 *
 *  * chat — [instructions] as the run's `promptPrefix`, [mcpServers] as its `ephemeralAgent.mcp`;
 *    the server turns both into an ephemeral agent (`instructions: promptPrefix || ''`,
 *    `mcpServers: new Set(ephemeralAgent?.mcp)`) — read in its source, not assumed;
 *  * tasks — [instructions] as the engine prompt's `system`, which the engine appends *after* the
 *    mission's charter rather than in place of it (see `EnginePromptRequest.system`). [mcpServers]
 *    does **not** cross over: a mission's tools are its ticked connectors, which is a per-session
 *    permission ruleset and a different mechanism entirely — one that already answers the same
 *    question, better, on that surface.
 *
 * @param enabled false parks the profile without erasing it, for the day a raw model is wanted.
 * @param instructions the system prompt. Blank sends nothing rather than an empty instruction.
 * @param mcpServers server names as the server itself spells them (`memoire`, `planificateur`) —
 *   they come from `GET /api/mcp/servers`, never typed from memory. Chat only.
 */
data class GlobalProfile(
    val enabled: Boolean = true,
    val instructions: String = "",
    val mcpServers: Set<String> = emptySet(),
) {
    /** Nothing to add to a request: the merge can then skip every allocation and stay a no-op. */
    val isEmpty: Boolean get() = !enabled || (instructions.isBlank() && mcpServers.isEmpty())

    companion object {
        val NONE = GlobalProfile(enabled = false)
    }
}
