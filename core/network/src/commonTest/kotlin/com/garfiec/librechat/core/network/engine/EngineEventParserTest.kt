package com.garfiec.librechat.core.network.engine

import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.network.sse.SseEvent
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The frames below are real, captured off the engine's global `/event` feed on 29/08/2026 during a
 * live turn, trimmed to the keys the parser reads. They are the contract: if the engine changes
 * shape, one of these breaks and names the field.
 */
class EngineEventParserTest {

    private val parser = EngineEventParser(Json { ignoreUnknownKeys = true })
    private fun frame(json: String) = parser.parse(SseEvent(data = json))

    @Test
    fun messageUpdatedOpensATurnAndNamesItsRole() {
        val parsed = frame(
            """{"id":"evt_1","type":"message.updated","properties":{"sessionID":"ses_1","info":{"id":"msg_u","role":"user","sessionID":"ses_1","agent":"build"}}}""",
        )
        assertEquals("ses_1", parsed?.sessionId)
        assertEquals(EngineStreamEvent.MessageStarted("msg_u", "user"), parsed?.event)
    }

    @Test
    fun partUpdatedCarriesTheWholeSnapshot() {
        val parsed = frame(
            """{"type":"message.part.updated","properties":{"sessionID":"ses_1","part":{"type":"text","text":"Compte de un a cinq","messageID":"msg_u","sessionID":"ses_1","id":"prt_1"}}}""",
        )
        val event = parsed?.event as EngineStreamEvent.PartUpdated
        assertEquals("msg_u", event.messageId)
        assertEquals("prt_1", event.partId)
        assertEquals("text", event.part.type)
        assertEquals("Compte de un a cinq", event.part.text)
    }

    @Test
    fun partDeltaIsTheTokenLevelAppend() {
        val parsed = frame(
            """{"type":"message.part.delta","properties":{"sessionID":"ses_1","messageID":"msg_a","partID":"prt_2","field":"text","delta":"Un"}}""",
        )
        assertEquals(EngineStreamEvent.PartDelta("msg_a", "prt_2", "text", "Un"), parsed?.event)
    }

    @Test
    fun aToolPartReportsItsNameCallAndStatus() {
        val parsed = frame(
            """{"type":"message.part.updated","properties":{"sessionID":"ses_1","part":{"type":"tool","tool":"memoire_lire","callID":"call_1","messageID":"msg_a","id":"prt_3","state":{"status":"completed","output":"…"}}}}""",
        )
        val part = (parsed?.event as EngineStreamEvent.PartUpdated).part
        assertEquals("memoire_lire", part.tool)
        assertEquals("call_1", part.callId)
        assertEquals("completed", part.status)
    }

    @Test
    fun sessionIdleEndsTheTurn() {
        val parsed = frame("""{"type":"session.idle","properties":{"sessionID":"ses_1"}}""")
        assertEquals(EngineStreamEvent.Idle, parsed?.event)
        assertEquals("ses_1", parsed?.sessionId)
    }

    @Test
    fun theSessionIsReportedSoTheClientCanKeepOnlyItsOwn() {
        // The feed is global: every frame names its session and the client filters on it.
        val parsed = frame("""{"type":"session.idle","properties":{"sessionID":"ses_autre"}}""")
        assertEquals("ses_autre", parsed?.sessionId)
    }

    @Test
    fun anUnrenderedTypeYieldsNoEventButStillNamesItsSession() {
        val parsed = frame("""{"type":"session.status","properties":{"sessionID":"ses_1","status":"busy"}}""")
        assertEquals("ses_1", parsed?.sessionId)
        assertNull(parsed?.event)
    }

    @Test
    fun aFrameThatIsNotAnEventEnvelopeParsesToNull() {
        assertNull(frame(""))
        assertNull(frame("pas du json"))
        assertNull(frame("""{"properties":{"sessionID":"ses_1"}}"""))
        assertNull(frame("""{"type":"message.updated"}"""))
    }

    @Test
    fun aPartMissingItsIdentifiersProducesNoEvent() {
        val parsed = frame(
            """{"type":"message.part.updated","properties":{"sessionID":"ses_1","part":{"type":"text","text":"x"}}}""",
        )
        assertEquals("ses_1", parsed?.sessionId)
        assertNull(parsed?.event)
    }

    @Test
    fun aQuestionCarriesTheWholeRequest() {
        // The shape of the engine's own OpenAPI, `EventQuestionAsked` (1.18.21).
        val parsed = frame(
            """{"id":"evt_q","type":"question.asked","properties":{"id":"que_1","sessionID":"ses_1",""" +
                """"questions":[{"question":"Quel compte ?","header":"Compte","options":[""" +
                """{"label":"Qonto (Recommended)","description":"Le compte pro"},{"label":"CMB","description":"Banque privée"}],""" +
                """"multiple":false}],"tool":{"messageID":"msg_a","callID":"call_9"}}}""",
        )
        assertEquals("ses_1", parsed?.sessionId)
        val request = (parsed?.event as EngineStreamEvent.QuestionAsked).request
        assertEquals("que_1", request.id)
        assertEquals("ses_1", request.sessionId)
        val question = request.questions.single()
        assertEquals("Compte", question.header)
        assertEquals(listOf("Qonto (Recommended)", "CMB"), question.options.map { it.label })
        assertEquals(false, question.multiple)
        // Absent on the wire, true on the engine's side: the free answer is offered by default.
        assertEquals(true, question.custom)
        assertEquals("call_9", request.tool?.callId)
    }

    @Test
    fun aQuestionWithNothingToAskProducesNoEvent() {
        val parsed = frame("""{"type":"question.asked","properties":{"id":"que_1","sessionID":"ses_1","questions":[]}}""")
        assertNull(parsed?.event)
    }

    @Test
    fun aRepliedOrRejectedQuestionCloses() {
        val replied = frame(
            """{"type":"question.replied","properties":{"sessionID":"ses_1","requestID":"que_1","answers":[["CMB"]]}}""",
        )
        val rejected = frame("""{"type":"question.rejected","properties":{"sessionID":"ses_1","requestID":"que_2"}}""")
        assertEquals(EngineStreamEvent.QuestionClosed("que_1"), replied?.event)
        assertEquals(EngineStreamEvent.QuestionClosed("que_2"), rejected?.event)
        assertEquals("ses_1", rejected?.sessionId)
    }
}
