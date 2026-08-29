package com.garfiec.librechat.core.network.engine

import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.network.sse.SseEvent
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The frames below are real, captured off the running engine on 29/08/2026 (the Stage-1 capture),
 * trimmed to the keys the parser reads. They are the contract; if the engine changes shape, one of
 * these breaks and names the field.
 */
class EngineEventParserTest {

    private val parser = EngineEventParser(Json { ignoreUnknownKeys = true })

    private fun frame(json: String) = parser.parse(SseEvent(data = json))

    @Test
    fun promptAdmittedCarriesTheUserMessageIdAndText() {
        val parsed = frame(
            """{"id":"evt_1","type":"session.next.prompt.admitted","durable":{"aggregateID":"ses_1","seq":1,"version":1},"data":{"sessionID":"ses_1","messageID":"msg_user","prompt":{"text":"Bonjour"},"delivery":"steer"}}""",
        )
        assertEquals(1L, parsed?.seq)
        assertEquals(EngineStreamEvent.PromptAdmitted(messageId = "msg_user", text = "Bonjour"), parsed?.event)
    }

    @Test
    fun stepStartedReportsTheModelDriverOfTheTurn() {
        val parsed = frame(
            """{"type":"session.next.step.started","durable":{"seq":3},"data":{"assistantMessageID":"msg_a","agent":"build","model":{"id":"claude-haiku-4-5","providerID":"hobbitton-gateway"}}}""",
        )
        assertEquals(
            EngineStreamEvent.StepStarted(assistantMessageId = "msg_a", agent = "build", modelId = "claude-haiku-4-5"),
            parsed?.event,
        )
    }

    @Test
    fun textDeltaIsTheIncrementalChunk() {
        val parsed = frame(
            """{"type":"session.next.text.delta","durable":{"seq":5},"data":{"assistantMessageID":"msg_a","textID":"text-0","delta":"Bon"}}""",
        )
        assertEquals(
            EngineStreamEvent.TextDelta(assistantMessageId = "msg_a", textId = "text-0", delta = "Bon"),
            parsed?.event,
        )
    }

    @Test
    fun textEndedCarriesTheWholeTextEvenWhenNoDeltaPreceded() {
        // The free `ling` model buffers: it went text.started -> text.ended with the full text and no
        // delta in between (captured 29/08). text.ended is authoritative in both worlds.
        val parsed = frame(
            """{"type":"session.next.text.ended","durable":{"seq":5,"version":1},"data":{"assistantMessageID":"msg_a","textID":"text-0","text":"Bonjour"}}""",
        )
        assertEquals(
            EngineStreamEvent.TextEnded(assistantMessageId = "msg_a", textId = "text-0", text = "Bonjour"),
            parsed?.event,
        )
    }

    @Test
    fun aToolAnnouncedByInputStartedThenSucceeds() {
        val started = frame(
            """{"type":"session.next.tool.input.started","durable":{"seq":7},"data":{"assistantMessageID":"msg_a","callID":"call_1","name":"qonto_list_transactions"}}""",
        )
        assertEquals(
            EngineStreamEvent.ToolStarted(assistantMessageId = "msg_a", callId = "call_1", name = "qonto_list_transactions"),
            started?.event,
        )
        val success = frame(
            """{"type":"session.next.tool.success","durable":{"seq":9},"data":{"assistantMessageID":"msg_a","callID":"call_1","result":"ok"}}""",
        )
        assertEquals(
            EngineStreamEvent.ToolEnded(assistantMessageId = "msg_a", callId = "call_1", ok = true, error = null),
            success?.event,
        )
    }

    @Test
    fun aToolCalledEventAlsoOpensAToolNamedByItsToolField() {
        val parsed = frame(
            """{"type":"session.next.tool.called","durable":{"seq":8},"data":{"assistantMessageID":"msg_a","callID":"call_2","tool":"memoire_ecrire","input":{}}}""",
        )
        assertEquals(
            EngineStreamEvent.ToolStarted(assistantMessageId = "msg_a", callId = "call_2", name = "memoire_ecrire"),
            parsed?.event,
        )
    }

    @Test
    fun toolFailedTakesTheErrorMessageWhetherStringOrObject() {
        val asString = frame(
            """{"type":"session.next.tool.failed","durable":{"seq":9},"data":{"assistantMessageID":"msg_a","callID":"c","error":"boom"}}""",
        )
        assertEquals(
            EngineStreamEvent.ToolEnded(assistantMessageId = "msg_a", callId = "c", ok = false, error = "boom"),
            asString?.event,
        )
        val asObject = frame(
            """{"type":"session.next.tool.failed","durable":{"seq":10},"data":{"assistantMessageID":"msg_a","callID":"c","error":{"message":"nope"}}}""",
        )
        assertEquals(
            EngineStreamEvent.ToolEnded(assistantMessageId = "msg_a", callId = "c", ok = false, error = "nope"),
            asObject?.event,
        )
    }

    @Test
    fun stepEndedNamesTheStopReason() {
        val parsed = frame(
            """{"type":"session.next.step.ended","durable":{"seq":6,"version":2},"data":{"assistantMessageID":"msg_a","finish":"stop","cost":0,"tokens":{"input":3318,"output":0}}}""",
        )
        assertEquals(EngineStreamEvent.StepEnded(assistantMessageId = "msg_a", finish = "stop"), parsed?.event)
    }

    @Test
    fun stepFailedSurfacesTheError() {
        val parsed = frame(
            """{"type":"session.next.step.failed","durable":{"seq":4},"data":{"assistantMessageID":"msg_a","error":"model refused"}}""",
        )
        assertEquals(EngineStreamEvent.Failed(assistantMessageId = "msg_a", error = "model refused"), parsed?.event)
    }

    @Test
    fun anUnmodelledTypeStillAdvancesTheCursorButProducesNoEvent() {
        val parsed = frame(
            """{"type":"session.next.context.updated","durable":{"seq":42},"data":{"sessionID":"ses_1"}}""",
        )
        assertEquals(42L, parsed?.seq)
        assertNull(parsed?.event)
    }

    @Test
    fun aFrameThatIsNotAnEventEnvelopeParsesToNull() {
        assertNull(frame(""))
        assertNull(frame("not json"))
        assertNull(frame("""{"durable":{"seq":1}}""")) // no type
    }

    @Test
    fun aKnownTypeWithoutItsRequiredIdsProducesNoEventButKeepsTheSeq() {
        val parsed = frame(
            """{"type":"session.next.text.delta","durable":{"seq":11},"data":{"textID":"text-0","delta":"x"}}""",
        )
        assertEquals(11L, parsed?.seq)
        assertNull(parsed?.event)
    }

    @Test
    fun textIdDefaultsToEmptyWhenAbsentRatherThanDroppingTheEvent() {
        val parsed = frame(
            """{"type":"session.next.text.started","durable":{"seq":4},"data":{"assistantMessageID":"msg_a"}}""",
        )
        assertTrue(parsed?.event is EngineStreamEvent.TextStarted)
        assertEquals("", (parsed?.event as EngineStreamEvent.TextStarted).textId)
    }
}
