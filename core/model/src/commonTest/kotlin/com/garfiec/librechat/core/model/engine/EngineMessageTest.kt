package com.garfiec.librechat.core.model.engine

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A transcript as the engine stores it (25/10/2026). The `summary` key is two things: a boolean on
 * the assistant message holding a compaction's summary, an object of diffs on a user message once
 * its turn ended. The evening the flag shipped, that object refused every finished conversation.
 */
class EngineMessageTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val transcript = """
        [
          {"info": {"id": "msg_u", "sessionID": "ses_1", "role": "user", "time": {"created": 1},
                    "summary": {"diffs": []}, "agent": "chat",
                    "model": {"providerID": "hobbitton-chat", "modelID": "deepseek-v4-pro"}},
           "parts": [{"id": "prt_u", "type": "text", "text": "J'aimerais acheter un parc"}]},
          {"info": {"id": "msg_a", "sessionID": "ses_1", "role": "assistant", "time": {"created": 2},
                    "modelID": "deepseek-v4-pro", "providerID": "hobbitton-chat", "error": null,
                    "tokens": {"input": 10, "output": 5, "reasoning": 0, "cache": {"read": 0, "write": 0}}},
           "parts": [{"id": "prt_a", "type": "text", "text": "Neuf ou d'occasion ?"}]},
          {"info": {"id": "msg_s", "sessionID": "ses_1", "role": "assistant", "time": {"created": 3},
                    "modelID": "deepseek-v4-pro", "providerID": "hobbitton-chat", "summary": true},
           "parts": [{"id": "prt_s", "type": "text", "text": "Résumé de la conversation."}]},
          {"info": {"id": "msg_n", "sessionID": "ses_1", "role": "assistant", "time": {"created": 4},
                    "summary": null},
           "parts": []}
        ]
    """

    @Test
    fun aUserMessageSummaryOfDiffsIsNotACompactionFlag() {
        val messages = json.decodeFromString(ListSerializer(EngineMessage.serializer()), transcript)
        assertEquals(listOf("msg_u", "msg_a", "msg_s", "msg_n"), messages.map { it.info.id })
        assertNull(messages[0].info.summary, "an object of diffs is not a flag")
        assertNull(messages[1].info.summary, "absent stays absent")
        assertEquals(true, messages[2].info.summary, "the compaction's summary keeps its flag")
        assertNull(messages[3].info.summary)
    }

    @Test
    fun theHistoryReplaysTheFlagOnTheSummaryMessageOnly() {
        val messages = json.decodeFromString(ListSerializer(EngineMessage.serializer()), transcript)
        val started = engineHistoryEvents(messages).filterIsInstance<EngineStreamEvent.MessageStarted>()
        assertFalse(started.first { it.messageId == "msg_u" }.summary)
        assertTrue(started.first { it.messageId == "msg_s" }.summary)
        assertEquals(EngineModelRef("hobbitton-chat", "deepseek-v4-pro"), started.first { it.messageId == "msg_a" }.model)
    }

    @Test
    fun theFlagWritesBackAsABoolean() {
        val info = EngineMessageInfo(id = "msg_s", role = "assistant", summary = true)
        assertTrue(json.encodeToString(EngineMessageInfo.serializer(), info).contains("\"summary\":true"))
    }
}
