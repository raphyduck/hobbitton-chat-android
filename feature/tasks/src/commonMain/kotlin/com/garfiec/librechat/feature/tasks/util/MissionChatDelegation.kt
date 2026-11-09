package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * A delegation's files (09/11/2026, Raphaël's capture: three documents the navigateur passed to
 * the conversation « sans erreur », and nothing on screen). A sub-agent runs in a session of its own
 * and the engine's `task` returns its text alone, so a file it handed back
 * (`fichiers_remettre_fichier`) sat on a tool part of that session, which the conversation never
 * shows. The `task` output names the session; once the call has ended the screen reads it once and
 * grafts its files onto the `task` part, where the fold draws them like any tool's.
 */

/**
 * The sub-agent's session a `task` call ran in, or null for any other call. The engine (1.18) opens
 * the tool's output with `<task id="<session>" state="…">`; a call that resumed a task names it in
 * its `task_id` argument, which is all there is while the call still runs.
 */
internal fun delegationOf(tool: String?, input: JsonElement?, output: String?): String? {
    if (tool != TASK_TOOL_NAME) return null
    val announced = output?.let { TASK_HEADER.find(it)?.groupValues?.get(1) }
    val resumed = ((input as? JsonObject)?.get(TASK_ID_ARGUMENT) as? JsonPrimitive)?.takeIf { it.isString }?.content
    return announced ?: resumed?.takeIf { it.isNotBlank() }
}

private const val TASK_TOOL_NAME = "task"
private const val TASK_ID_ARGUMENT = "task_id"
private val TASK_HEADER = Regex("""<task id="([^"\s]+)""")

/** The part with the files its delegation handed back, when they have been read; itself otherwise. */
internal fun ChatPart.withDelegatedFiles(files: Map<String, List<ChatPart.Attachment>>): ChatPart {
    if (this !is ChatPart.Tool || delegation == null) return this
    val found = files[delegation] ?: return this
    return if (attachments == found) this else copy(attachments = found)
}

/**
 * Records what the sub-agent's session [delegation] handed back and grafts it onto every `task`
 * part that ran there, so the files draw under the fold without a second rendering path.
 */
fun MissionChatState.withDelegatedFiles(delegation: String, files: List<ChatPart.Attachment>): MissionChatState {
    val known = delegatedFiles + (delegation to files)
    return copy(
        delegatedFiles = known,
        turns = turns.map { turn ->
            if (turn !is ChatTurn.Assistant || turn.parts.none { it is ChatPart.Tool && it.delegation == delegation }) {
                turn
            } else {
                turn.copy(parts = turn.parts.map { it.withDelegatedFiles(known) })
            }
        },
    )
}

/** The delegations whose session has not been read yet: a `task` that ended, in a session named. */
fun MissionChatState.delegationsToRead(): Set<String> =
    turns.asSequence()
        .filterIsInstance<ChatTurn.Assistant>()
        .flatMap { it.parts.asSequence() }
        .filterIsInstance<ChatPart.Tool>()
        .filter { it.state != ToolState.RUNNING }
        .mapNotNull { it.delegation }
        .filterNot { it in delegatedFiles }
        .toSet()

/**
 * The files the tools of a sub-agent's session handed back, read off its replayed transcript
 * ([delegation] keeps their ids apart from the conversation's own parts). The session's text,
 * reasoning and tool rows stay where they are: the `task` summary stands for them.
 */
fun delegatedFilesOf(delegation: String, events: List<EngineStreamEvent>): List<ChatPart.Attachment> =
    events.asSequence()
        .filterIsInstance<EngineStreamEvent.PartUpdated>()
        .filter { it.part.type == "tool" && it.part.attachments.isNotEmpty() }
        .flatMap { event ->
            event.part.attachments.asSequence().mapIndexedNotNull { index, file ->
                file.url?.let {
                    ChatPart.Attachment("$delegation#${event.partId}#$index", file.mime ?: DEFAULT_MIME, it, shownFileName(file.filename))
                }
            }
        }
        .toList()
