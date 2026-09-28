package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EngineMessage
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue

/**
 * The two ways this app drives the engine (D-077): a **chat** and a **task**.
 *
 * One engine, two profiles. LibreChat is gone; a conversation is now an engine session like a
 * mission, and what keeps the two apart is the profile each one is created with:
 *
 *  * [agent] — the engine agent the session runs on. `chat` has no shell, no local file tools and no
 *    web fetch; its tools are the memory, the scheduler and the annuaire, bounded by the session's
 *    perimeter. `mission` is the Tasks tab's, unchanged.
 *  * [providerId] — where its model comes from, and therefore which budget it spends. A chat's
 *    model always comes from `hobbitton-chat`, which carries the chat budget; a task's from the
 *    gateway.
 */
enum class EngineProfile(val agent: String, val providerId: String) {
    TASK(agent = MISSION_AGENT, providerId = GATEWAY_PROVIDER),
    CHAT(agent = CHAT_AGENT, providerId = CHAT_PROVIDER),
}

/** The engine agent a chat session runs on — the server's name for it, exactly. */
const val CHAT_AGENT = "chat"

/** The engine agent every mission launched from this app runs on. */
const val MISSION_AGENT = "mission"

/** The provider a chat's model must come from: it carries the chat budget. */
const val CHAT_PROVIDER = "hobbitton-chat"

/** The provider the tasks run on: the platform's gateway. */
const val GATEWAY_PROVIDER = "hobbitton-gateway"

/**
 * Whether a declared provider is offered to [profile]'s model picker.
 *
 * A chat sees `hobbitton-chat` and nothing else. A task sees every declared provider **except**
 * that one: the gateway today, and whatever else the deployment declares for its missions tomorrow
 * — narrowing tasks to the gateway by name would silently drop a provider added for them, while a
 * task running on the chat's provider would spend the chat budget.
 */
fun offersProvider(profile: EngineProfile, providerId: String): Boolean = when (profile) {
    EngineProfile.CHAT -> providerId == CHAT_PROVIDER
    EngineProfile.TASK -> providerId != CHAT_PROVIDER
}

/**
 * A model a chat may run on: the same model id, on the chat's provider.
 *
 * `hobbitton-chat` lists the same model ids as the gateway, so a model named on the gateway — a
 * stale pick, a session's last model read off its transcript — maps one to one. The alternative,
 * sending it as is, would bill a conversation to the tasks' budget.
 */
fun EngineModelRef.forChat(): EngineModelRef =
    if (providerId == CHAT_PROVIDER) this else copy(providerId = CHAT_PROVIDER)

/**
 * The perimeter of a chat session: every connector the scheduler opens to a chat, sorted.
 *
 * All of them, always — a chat is not configured connector by connector, it gets what a
 * conversation may reach (D-077). And **only** them: a connector without the `chat` flag (`shell`,
 * the workspace, the web) never enters a chat's rules nor its annuaire scope, whatever the engine's
 * profile would have refused on its own.
 */
fun ConnectorCatalogue.chatPerimeter(): List<String> =
    connecteurs.filterValues { it.chat }.keys.sorted()

/** What a session is, as far as this app's lists are concerned. */
enum class EngineSessionKind { CHAT, TASK }

/**
 * Tells a chat from a task — the rule the drawer's conversations and the Tasks tab both sort by.
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
 * Null when none of that says anything — a session with no message yet. The caller decides what
 * an unknown is worth (the drawer leaves it out, the Tasks tab keeps it).
 */
fun classifySession(
    title: String?,
    recorded: EngineSessionKind?,
    messages: List<EngineMessage>?,
): EngineSessionKind? {
    recorded?.let { return it }
    if (title != null && isScheduledRun(title)) return EngineSessionKind.TASK
    val infos = messages.orEmpty().map { it.info }
    infos.firstNotNullOfOrNull { info -> info.agent?.takeIf { it.isNotBlank() } }?.let { agent ->
        return if (agent == CHAT_AGENT) EngineSessionKind.CHAT else EngineSessionKind.TASK
    }
    infos.firstNotNullOfOrNull { info -> info.providerId?.takeIf { it.isNotBlank() } }?.let { provider ->
        return if (provider == CHAT_PROVIDER) EngineSessionKind.CHAT else EngineSessionKind.TASK
    }
    return null
}
