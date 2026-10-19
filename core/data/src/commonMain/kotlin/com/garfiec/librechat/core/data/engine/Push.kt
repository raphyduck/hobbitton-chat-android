package com.garfiec.librechat.core.data.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * What the server pushes (19/10/2026): a decision waiting for the person in a conversation the
 * scheduler opened, or a question an agent asked. Published on the scheduler's ntfy topic, which
 * the app follows itself (the person installs and configures nothing).
 */
data class PushMessage(
    /** ntfy's id: the stream resumes after it. */
    val id: String,
    val title: String,
    val text: String,
    /** The conversation to open, from the notification's link; null when it names none. */
    val sessionId: String?,
    val kind: PushKind,
)

enum class PushKind { DECISION, QUESTION, OTHER }

private val ntfyJson = Json { ignoreUnknownKeys = true }

/**
 * One line of ntfy's JSON stream, or null for anything but a message (`open`, `keepalive`) or a
 * line that is not JSON. The kind travels as a tag (`decision`, `question`), the conversation as
 * the click link (`at.hobbitton.chat://conversation/<session>`).
 */
fun parsePushLine(line: String): PushMessage? {
    val frame = runCatching { ntfyJson.parseToJsonElement(line).jsonObject }.getOrNull() ?: return null
    if (frame.text("event") != "message") return null
    val id = frame.text("id") ?: return null
    val tags = (frame["tags"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
    val kind = when {
        "decision" in tags -> PushKind.DECISION
        "question" in tags -> PushKind.QUESTION
        else -> PushKind.OTHER
    }
    return PushMessage(
        id = id,
        title = frame.text("title").orEmpty(),
        text = frame.text("message").orEmpty(),
        sessionId = frame.text("click")?.let(::sessionOfConversationUri),
        kind = kind,
    )
}

/** [sessionOfConversationLink] for a link written out as one string. */
fun sessionOfConversationUri(uri: String): String? {
    val scheme = uri.substringBefore("://", missingDelimiterValue = "")
    val rest = uri.substringAfter("://", missingDelimiterValue = "")
    val host = rest.substringBefore('/')
    val path = rest.removePrefix(host).substringBefore('?')
    return sessionOfConversationLink(scheme, host, path)
}

/**
 * Whether a push becomes a notification. Not while the app is on screen: the drawer shows the
 * conversation unread and the in-app watcher chimes. Not a question while the in-app watcher is
 * following the engine: it notifies that question itself, and the person would hear it twice.
 */
fun shouldNotifyPush(kind: PushKind, appInForeground: Boolean, watcherRunning: Boolean): Boolean = when {
    appInForeground -> false
    kind == PushKind.QUESTION && watcherRunning -> false
    else -> true
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
