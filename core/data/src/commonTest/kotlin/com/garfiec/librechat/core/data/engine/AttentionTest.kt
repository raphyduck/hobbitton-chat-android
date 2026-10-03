package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EngineQuestionInfo
import com.garfiec.librechat.core.model.engine.EngineQuestionRequest
import com.garfiec.librechat.core.model.engine.EngineStreamEvent
import com.garfiec.librechat.core.network.engine.EngineStreamClient.SessionEvent
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** When a question or a finished reply rings, and what the watcher makes of the engine's feed. */
class AttentionTest {

    private class RecordingNotifier(override var inForeground: Boolean = true) : AttentionNotifier {
        val calls = mutableListOf<String>()
        val questions = mutableListOf<Pair<OpenConversation, String>>()

        override fun chime() {
            calls += "chime"
        }

        override fun showQuestion(conversation: OpenConversation, requestId: String, question: String) {
            calls += "question:$requestId"
            questions += conversation to question
        }

        override fun showReplyReady(conversation: OpenConversation) {
            calls += "reply:${conversation.sessionId}"
        }

        override fun cancelQuestion(requestId: String) {
            calls += "cancel:$requestId"
        }

        override fun cancelReplyReady(sessionId: String) {
            calls += "cancelReply:$sessionId"
        }
    }

    private fun request(id: String, session: String = "ses_1") = EngineQuestionRequest(
        id = id,
        sessionId = session,
        questions = listOf(EngineQuestionInfo(question = "Quel compte ?", header = "Compte")),
    )

    private val conversation = OpenConversation("ses_1", "Relevés", EngineSessionKind.TASK)

    @Test
    fun aQuestionInTheConversationOnScreenOnlyChimes() = runTest {
        val notifier = RecordingNotifier()
        val signals = AttentionSignals(notifier) { true }
        signals.enter("ses_1")
        notifier.calls.clear()

        signals.questionAsked(request("que_1")) { conversation }

        assertEquals(listOf("chime"), notifier.calls)
    }

    @Test
    fun aQuestionElsewhereOrOutOfSightIsNotified() = runTest {
        val notifier = RecordingNotifier()
        val signals = AttentionSignals(notifier) { true }
        signals.enter("ses_autre")
        signals.questionAsked(request("que_1")) { conversation }

        signals.enter("ses_1")
        notifier.inForeground = false
        signals.questionAsked(request("que_2")) { conversation }

        assertEquals(listOf("question:que_1", "question:que_2"), notifier.calls.filter { it.startsWith("question") })
        assertEquals("Quel compte ?", notifier.questions.first().second)
    }

    @Test
    fun aQuestionRingsOnceHoweverOftenItIsAnnounced() = runTest {
        val notifier = RecordingNotifier(inForeground = false)
        val signals = AttentionSignals(notifier) { true }

        signals.questionAsked(request("que_1")) { conversation }
        signals.questionAsked(request("que_1")) { conversation }

        assertEquals(listOf("question:que_1"), notifier.calls)
    }

    @Test
    fun theSwitchOffSilencesEverything() = runTest {
        val notifier = RecordingNotifier(inForeground = false)
        val signals = AttentionSignals(notifier) { false }

        signals.questionAsked(request("que_1")) { conversation }
        signals.replyReady("ses_1") { conversation }

        assertTrue(notifier.calls.isEmpty())
    }

    @Test
    fun aReplyRingsOnlyWhenNobodyIsLookingAtIt() = runTest {
        val notifier = RecordingNotifier()
        val signals = AttentionSignals(notifier) { true }
        signals.enter("ses_1")
        signals.replyReady("ses_1") { conversation }
        signals.leave("ses_1")
        signals.replyReady("ses_1") { conversation }

        assertEquals(listOf("cancelReply:ses_1", "reply:ses_1"), notifier.calls)
    }

    @Test
    fun leavingAnotherConversationDoesNotForgetTheOneOnScreen() {
        val signals = AttentionSignals(RecordingNotifier()) { true }
        signals.enter("ses_2")
        signals.leave("ses_1")
        assertEquals("ses_2", signals.onScreen.value)
    }

    @Test
    fun theWatcherRingsWhatWasPendingThenWhatTheFeedSays() = runTest {
        val engine = MockEngine { request ->
            val title = if (request.url.encodedPath.endsWith("ses_2")) "Courrier" else "Relevés"
            respond(
                content = """{"id":"x","title":"$title"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val kinds = InMemorySessionKinds(mapOf("ses_2" to EngineSessionKind.CHAT))
        val repository = testMissionRepository(engine, kinds = kinds)
        val notifier = RecordingNotifier(inForeground = false)
        val watcher = EngineAttentionWatcher(
            repository = repository,
            signals = AttentionSignals(notifier) { true },
            events = {
                flowOf(
                    SessionEvent("ses_2", EngineStreamEvent.QuestionAsked(request("que_2", "ses_2"))),
                    SessionEvent("ses_2", EngineStreamEvent.Idle),
                    SessionEvent("ses_2", EngineStreamEvent.QuestionClosed("que_2")),
                )
            },
            pending = { listOf(request("que_1")) },
        )

        watcher.watch()

        assertEquals(listOf("question:que_1", "question:que_2", "cancel:que_2"), notifier.calls)
        assertEquals(
            listOf(
                OpenConversation("ses_1", "Relevés", null),
                OpenConversation("ses_2", "Courrier", EngineSessionKind.CHAT),
            ),
            notifier.questions.map { it.first },
        )
    }
}
