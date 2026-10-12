package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EngineMessage
import com.garfiec.librechat.core.model.engine.EngineModelRef

/**
 * The engine agent every conversation of this app runs on (D-077, D-082). Since 12/10/2026 chats and
 * tasks are one kind of conversation, as on Claude.ai: the server's `chat` profile carries what the
 * tasks' `mission` profile did, and the session's own rules close what is not ticked. `mission` is
 * now the scheduled missions' profile only, and this app never sends a turn on it.
 */
const val CHAT_AGENT = "chat"

/**
 * The provider every conversation's model comes from: it carries the conversations' budget. The
 * scheduled missions spend on the gateway's, a second key (D-082).
 */
const val CHAT_PROVIDER = "hobbitton-chat"

/**
 * A model a conversation may run on: the same model id, on the conversations' provider.
 *
 * `hobbitton-chat` lists the same model ids as the gateway, so a model named on the gateway (a
 * stale pick, a session's last model read off its transcript, a scheduled run's) maps one to one.
 * The alternative, sending it as is, would bill a conversation to the scheduled missions' budget.
 */
fun EngineModelRef.forChat(): EngineModelRef =
    if (providerId == CHAT_PROVIDER) this else copy(providerId = CHAT_PROVIDER)

/**
 * What a session was before D-082: started as a chat, or as a task (by this app, by the scheduler,
 * by another client). Since chats and tasks became one conversation (12/10/2026) the kind no
 * longer picks a screen nor a list; it is kept because the drawer leaves out a session nothing can
 * classify yet (no message), and because devices already recorded thousands of verdicts.
 */
enum class EngineSessionKind { CHAT, TASK }

/**
 * Tells a chat from a task, and (by returning null) a session that has said nothing yet.
 *
 * In order, first answer wins:
 *
 *  1. **what this app recorded** when it created the session ([recorded]): exact, and free. Every
 *     chat started from this device lands here.
 *  2. **the scheduler's title shape**: a run the scheduler started is a task, without reading a
 *     single message.
 *  3. **the agent written on the session's messages**: `chat` is a chat, any other agent a task.
 *     The engine writes the agent on every message it records, so this is what classifies a chat
 *     started on another device, or before this device recorded anything.
 *  4. **the provider** that answered, when no message names an agent: `hobbitton-chat` only ever
 *     serves chats.
 *
 * Null when none of that says anything: a session with no message yet, which the drawer leaves
 * out.
 */
fun classifySession(
    title: String?,
    recorded: EngineSessionKind?,
    messages: List<EngineMessage>?,
): EngineSessionKind? {
    recorded?.let { return it }
    if (title != null && isScheduledRun(title)) return EngineSessionKind.TASK
    agentWrittenOn(messages)?.let { agent ->
        return if (agent == CHAT_AGENT) EngineSessionKind.CHAT else EngineSessionKind.TASK
    }
    val provider = messages.orEmpty().firstNotNullOfOrNull { message ->
        message.info.providerId?.takeIf { it.isNotBlank() }
    }
    return provider?.let { if (it == CHAT_PROVIDER) EngineSessionKind.CHAT else EngineSessionKind.TASK }
}

/**
 * The agent the engine wrote on a session's messages: the first user message's, or else the first
 * message that names one. Null when no message names an agent (a session with no message yet).
 *
 * The first **user** message is the session's own agent: it is the one its creator prompted it
 * with, before any later turn could have been sent on another agent.
 */
internal fun agentWrittenOn(messages: List<EngineMessage>?): String? {
    val infos = messages.orEmpty().map { it.info }
    return (infos.filter { it.role == USER_ROLE } + infos)
        .firstNotNullOfOrNull { info -> info.agent?.takeIf { it.isNotBlank() } }
}

private const val USER_ROLE = "user"
