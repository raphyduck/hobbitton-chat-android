package com.garfiec.librechat.core.model.engine

/**
 * What a mission is doing, as the app is entitled to claim it.
 *
 * This exists because the obvious rule is wrong, and wrong in the direction that hides failures.
 * The engine has no per-session state: `GET /session/status` lists the sessions that are *active*,
 * so a session missing from that map is simply not running. Reading that as « finished » is the
 * mistake the server-side scheduler made, and it cost a whole night: on 21/08/2026 the gateway had
 * lost its database, every model call was refused, and the nightly mission was recorded **OK, 3,0 s,
 * 0 token**. Nothing was wrong with the code that watched it. It was watching the wrong thing.
 *
 * So « not running » is decided here by three questions, in this order: did the engine file an
 * error, did the mission consume anything at all, and only then — is it finished.
 */
sealed interface MissionState {
    /** The engine reports it as active. [detail] carries `retry`, `running`… as the engine words it. */
    data class Running(val detail: String?) : MissionState

    /** Finished, having actually done something. */
    data class Succeeded(val tokens: Long) : MissionState

    /** Finished badly, or never really started. [reason] is meant to be shown as-is. */
    data class Failed(val reason: String, val tokens: Long) : MissionState

    /** No session yet — a mission that has been created but not launched. */
    data object Idle : MissionState
}

/** Truncates a nested-JSON engine error into something a list row can show. */
private fun summarise(text: String, limit: Int = 200): String {
    val flat = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    return if (flat.length <= limit) flat else flat.take(limit - 1) + "…"
}

/**
 * The error the engine filed on this session, if any.
 *
 * A failed model call does not crash the session: the engine puts the error on the last assistant
 * message and lets the session fall idle, in milliseconds. Read the status map alone and this is
 * indistinguishable from a mission that ran and finished.
 */
fun engineError(messages: List<EngineMessage>): String? =
    messages.asReversed()
        .firstNotNullOfOrNull { it.info.error }
        ?.let { error -> summarise(error.data?.message ?: error.name ?: "erreur du moteur") }

/** Everything the mission consumed, cache included. */
fun tokensUsed(messages: List<EngineMessage>): Long =
    messages.sumOf { it.info.tokens?.total ?: 0L }

/**
 * Decides the state of one mission from what the engine will actually tell us about it.
 *
 * [status] is this session's entry in the global status map — null when it is not listed, which is
 * the normal case for anything that has stopped.
 *
 * The zero-token rule is a backstop, and deliberately not derived from the error field: it does not
 * depend on how the engine chooses to report failures today. A mission that never spoke to a model
 * did not work, whatever the reason. It costs an implausible false alarm — a prompt sent to a model
 * always consumes at least its input tokens — and it buys never again calling a silent nothing a
 * success.
 */
fun judgeMission(
    status: EngineSessionStatus?,
    messages: List<EngineMessage>,
    hasSession: Boolean = true,
): MissionState {
    if (!hasSession) return MissionState.Idle
    if (status != null && status.type != "idle") {
        return MissionState.Running(status.message ?: status.type)
    }

    val tokens = tokensUsed(messages)
    engineError(messages)?.let { return MissionState.Failed(it, tokens) }
    if (messages.isEmpty()) return MissionState.Running(null)
    if (tokens == 0L) return MissionState.Failed("aucun appel modèle (0 jeton consommé)", tokens)
    return MissionState.Succeeded(tokens)
}
