package com.garfiec.librechat.core.data.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The server's push notifications, as ntfy streams them (19/10/2026). */
class PushTest {

    @Test
    fun `a decision names its conversation`() {
        val line = """{"id":"abc123","time":1,"event":"message","topic":"t","title":"À décider : devis",""" +
            """"message":"Une décision t'attend dans Eva.","tags":["bell","decision"],""" +
            """"click":"at.hobbitton.chat://conversation/ses_eac49655fffeyMLLs9ywOJiMSE"}"""
        val message = parsePushLine(line)!!
        assertEquals("abc123", message.id)
        assertEquals("À décider : devis", message.title)
        assertEquals(PushKind.DECISION, message.kind)
        assertEquals("ses_eac49655fffeyMLLs9ywOJiMSE", message.sessionId)
    }

    @Test
    fun `keepalives and noise are not messages`() {
        assertNull(parsePushLine("""{"id":"k","time":1,"event":"keepalive","topic":"t"}"""))
        assertNull(parsePushLine("""{"id":"o","time":1,"event":"open","topic":"t"}"""))
        assertNull(parsePushLine("not json"))
    }

    @Test
    fun `a link to anything else opens no conversation`() {
        val line = """{"id":"x","event":"message","message":"m","tags":["question"],"click":"https://example.com/ses_abcdefgh"}"""
        val message = parsePushLine(line)!!
        assertEquals(PushKind.QUESTION, message.kind)
        assertNull(message.sessionId)
    }

    @Test
    fun `the app on screen or its own watcher keep a push quiet`() {
        assertFalse(shouldNotifyPush(PushKind.DECISION, appInForeground = true, watcherRunning = false))
        assertFalse(shouldNotifyPush(PushKind.QUESTION, appInForeground = false, watcherRunning = true))
        assertTrue(shouldNotifyPush(PushKind.DECISION, appInForeground = false, watcherRunning = true))
        assertTrue(shouldNotifyPush(PushKind.QUESTION, appInForeground = false, watcherRunning = false))
    }
}
