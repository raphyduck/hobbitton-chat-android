package com.librechat.android.core.network.sse

import com.librechat.android.core.model.StreamEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test

class SseEventMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private lateinit var mapper: SseEventMapper

    @Before
    fun setUp() {
        mapper = SseEventMapper(json)
    }

    // --- Content Delta (LangGraph) ---

    @Test
    fun `maps on_message_delta to ContentDelta`() {
        val event = SseEvent(
            event = "",
            data = """{"event":"on_message_delta","data":{"id":"step_1","delta":{"content":[{"type":"text","text":"Hello "}]}}}""",
        )
        val result = mapper.map(event)
        assertThat(result).isInstanceOf(StreamEvent.ContentDelta::class.java)
        val delta = result as StreamEvent.ContentDelta
        assertThat(delta.chunk).isEqualTo("Hello ")
        assertThat(delta.messageId).isEqualTo("step_1")
    }

    @Test
    fun `maps on_message_delta with multiple content parts`() {
        val event = SseEvent(
            event = "",
            data = """{"event":"on_message_delta","data":{"id":"step_1","delta":{"content":[{"type":"text","text":"Hello "},{"type":"text","text":"world"}]}}}""",
        )
        val result = mapper.map(event) as StreamEvent.ContentDelta
        assertThat(result.chunk).isEqualTo("Hello world")
    }

    @Test
    fun `returns null for empty content delta`() {
        val event = SseEvent(
            event = "",
            data = """{"event":"on_message_delta","data":{"id":"step_1","delta":{"content":[{"type":"image_url","url":"http://x.com"}]}}}""",
        )
        val result = mapper.map(event)
        assertThat(result).isNull()
    }

    // --- Thinking Delta ---

    @Test
    fun `maps on_reasoning_delta to ThinkingDelta`() {
        val event = SseEvent(
            event = "",
            data = """{"event":"on_reasoning_delta","data":{"id":"step_1","delta":{"content":[{"type":"think","think":"Let me consider..."}]}}}""",
        )
        val result = mapper.map(event)
        assertThat(result).isInstanceOf(StreamEvent.ThinkingDelta::class.java)
        assertThat((result as StreamEvent.ThinkingDelta).chunk).isEqualTo("Let me consider...")
    }

    // --- Tool Calls ---

    @Test
    fun `maps on_run_step to ToolCallStart`() {
        val event = SseEvent(
            event = "",
            data = """{"event":"on_run_step","data":{"id":"step_1","stepDetails":{"type":"tool_calls","tool_calls":[{"id":"call_1","name":"web_search","args":"{\"query\":\"kotlin\"}"}]},"agentId":"agent_1","groupId":1}}""",
        )
        val result = mapper.map(event)
        assertThat(result).isInstanceOf(StreamEvent.ToolCallStart::class.java)
        val tc = result as StreamEvent.ToolCallStart
        assertThat(tc.toolCallId).isEqualTo("call_1")
        assertThat(tc.toolName).isEqualTo("web_search")
        assertThat(tc.agentId).isEqualTo("agent_1")
        assertThat(tc.groupId).isEqualTo(1)
    }

    @Test
    fun `maps on_run_step_completed to ToolCallComplete`() {
        val event = SseEvent(
            event = "",
            data = """{"event":"on_run_step_completed","data":{"result":{"id":"step_1","tool_call":{"id":"call_1","name":"search","output":"Results here"}},"agentId":"agent_1","groupId":1}}""",
        )
        val result = mapper.map(event)
        assertThat(result).isInstanceOf(StreamEvent.ToolCallComplete::class.java)
        val tc = result as StreamEvent.ToolCallComplete
        assertThat(tc.toolCallId).isEqualTo("call_1")
        assertThat(tc.output).isEqualTo("Results here")
    }

    @Test
    fun `tool call output handles JSON object values`() {
        val event = SseEvent(
            event = "",
            data = """{"event":"on_run_step_completed","data":{"result":{"id":"s","tool_call":{"id":"c1","output":{"url":"http://img.com/x.png"}}}}}""",
        )
        val result = mapper.map(event) as StreamEvent.ToolCallComplete
        assertThat(result.output).contains("http://img.com/x.png")
    }

    // --- Agent Context Tracking ---

    @Test
    fun `tracks agentId from on_run_step and applies to subsequent content events`() {
        // Step 1: on_run_step sets agent context
        mapper.map(
            SseEvent(
                event = "",
                data = """{"event":"on_run_step","data":{"id":"s1","stepDetails":{"type":"tool_calls","tool_calls":[{"id":"c1","name":"search","args":""}]},"agentId":"agent_A","groupId":2}}""",
            ),
        )

        // Step 2: on_message_delta doesn't carry agentId — should inherit from step
        val delta = mapper.map(
            SseEvent(
                event = "",
                data = """{"event":"on_message_delta","data":{"id":"s2","delta":{"content":[{"type":"text","text":"Hi"}]}}}""",
            ),
        )

        assertThat(delta).isInstanceOf(StreamEvent.ContentDelta::class.java)
        assertThat((delta as StreamEvent.ContentDelta).agentId).isEqualTo("agent_A")
        assertThat(delta.groupId).isEqualTo(2)
    }

    @Test
    fun `resetState clears tracked agent context`() {
        mapper.map(
            SseEvent(
                event = "",
                data = """{"event":"on_run_step","data":{"id":"s1","stepDetails":{"type":"tool_calls","tool_calls":[{"id":"c1","name":"t","args":""}]},"agentId":"agent_A","groupId":1}}""",
            ),
        )

        mapper.resetState()

        val delta = mapper.map(
            SseEvent(
                event = "",
                data = """{"event":"on_message_delta","data":{"id":"s2","delta":{"content":[{"type":"text","text":"Hi"}]}}}""",
            ),
        ) as StreamEvent.ContentDelta

        assertThat(delta.agentId).isNull()
        assertThat(delta.groupId).isNull()
    }

    // --- Control Events ---

    @Test
    fun `maps final event with full payload`() {
        val event = SseEvent(
            event = "",
            data = """{"final":true,"conversation":{"conversationId":"c1","title":"Chat"},"requestMessage":{"messageId":"m1","conversationId":"c1","text":"Hello","isCreatedByUser":true},"responseMessage":{"messageId":"m2","conversationId":"c1","text":"Hi there","isCreatedByUser":false}}""",
        )
        val result = mapper.map(event)
        assertThat(result).isInstanceOf(StreamEvent.Final::class.java)
        val final = result as StreamEvent.Final
        assertThat(final.conversation?.conversationId).isEqualTo("c1")
        assertThat(final.requestMessage?.text).isEqualTo("Hello")
        assertThat(final.responseMessage?.text).isEqualTo("Hi there")
        assertThat(final.hasParseErrors).isFalse()
    }

    @Test
    fun `maps final event with legacy message field`() {
        val event = SseEvent(
            event = "",
            data = """{"final":true,"message":{"messageId":"m1","conversationId":"c1","text":"Done"}}""",
        )
        val final = mapper.map(event) as StreamEvent.Final
        assertThat(final.message?.text).isEqualTo("Done")
    }

    @Test
    fun `maps created event with nested message object`() {
        val event = SseEvent(
            event = "",
            data = """{"created":{"message":{"conversationId":"c1","messageId":"m1","parentMessageId":"m0"}}}""",
        )
        val result = mapper.map(event) as StreamEvent.Created
        assertThat(result.conversationId).isEqualTo("c1")
        assertThat(result.messageId).isEqualTo("m1")
        assertThat(result.parentMessageId).isEqualTo("m0")
    }

    @Test
    fun `maps created event with boolean flag format`() {
        val event = SseEvent(
            event = "",
            data = """{"created":true,"message":{"conversationId":"c1","messageId":"m1","parentMessageId":"m0"}}""",
        )
        val result = mapper.map(event) as StreamEvent.Created
        assertThat(result.conversationId).isEqualTo("c1")
    }

    @Test
    fun `maps error event`() {
        val event = SseEvent(
            event = "",
            data = """{"error":"Rate limit exceeded"}""",
        )
        val result = mapper.map(event) as StreamEvent.Error
        assertThat(result.message).isEqualTo("Rate limit exceeded")
    }

    @Test
    fun `maps sync event with aggregatedContent`() {
        val event = SseEvent(
            event = "",
            data = """{"sync":true,"resumeState":{"aggregatedContent":[{"type":"text","text":"Previously streamed text"}]}}""",
        )
        val result = mapper.map(event) as StreamEvent.Sync
        assertThat(result.aggregatedContent).hasSize(1)
        assertThat(result.aggregatedContent[0].text).isEqualTo("Previously streamed text")
    }

    // --- Attachment Events ---

    @Test
    fun `maps SSE-level attachment event`() {
        val event = SseEvent(
            event = "attachment",
            data = """{"file_id":"f1","filename":"image.png","type":"image/png","filepath":"/files/image.png","width":800,"height":600}""",
        )
        val result = mapper.map(event) as StreamEvent.AttachmentCreated
        assertThat(result.fileId).isEqualTo("f1")
        assertThat(result.filename).isEqualTo("image.png")
        assertThat(result.width).isEqualTo(800)
        assertThat(result.height).isEqualTo(600)
    }

    @Test
    fun `maps librechat attachment event variant`() {
        val event = SseEvent(
            event = "librechat:attachment",
            data = """{"file_id":"f2","filename":"doc.pdf","type":"application/pdf"}""",
        )
        val result = mapper.map(event) as StreamEvent.AttachmentCreated
        assertThat(result.fileId).isEqualTo("f2")
    }

    // --- Legacy Flat Format ---

    @Test
    fun `maps legacy content event`() {
        val event = SseEvent(
            event = "",
            data = """{"type":"content","text":"Legacy text","messageId":"m1"}""",
        )
        val result = mapper.map(event) as StreamEvent.ContentDelta
        assertThat(result.chunk).isEqualTo("Legacy text")
        assertThat(result.messageId).isEqualTo("m1")
    }

    @Test
    fun `maps legacy thinking event`() {
        val event = SseEvent(
            event = "",
            data = """{"type":"thinking","text":"Hmm...","metadata":{"agentId":"a1","groupId":1}}""",
        )
        val result = mapper.map(event) as StreamEvent.ThinkingDelta
        assertThat(result.chunk).isEqualTo("Hmm...")
        assertThat(result.agentId).isEqualTo("a1")
    }

    @Test
    fun `maps legacy tool_call_start event`() {
        val event = SseEvent(
            event = "",
            data = """{"type":"tool_call_start","toolCallId":"tc1","toolName":"calculator","input":"2+2"}""",
        )
        val result = mapper.map(event) as StreamEvent.ToolCallStart
        assertThat(result.toolCallId).isEqualTo("tc1")
        assertThat(result.toolName).isEqualTo("calculator")
        assertThat(result.input).isEqualTo("2+2")
    }

    @Test
    fun `maps legacy text-only fallback event`() {
        val event = SseEvent(
            event = "",
            data = """{"text":"Fallback content"}""",
        )
        val result = mapper.map(event) as StreamEvent.ContentDelta
        assertThat(result.chunk).isEqualTo("Fallback content")
    }

    // --- Edge Cases ---

    @Test
    fun `returns null for blank data`() {
        assertThat(mapper.map(SseEvent(event = "", data = ""))).isNull()
        assertThat(mapper.map(SseEvent(event = "", data = "  "))).isNull()
    }

    @Test
    fun `returns null for DONE marker`() {
        assertThat(mapper.map(SseEvent(event = "", data = "[DONE]"))).isNull()
    }

    @Test
    fun `returns Error for unparseable JSON`() {
        val result = mapper.map(SseEvent(event = "", data = "not json at all"))
        assertThat(result).isInstanceOf(StreamEvent.Error::class.java)
        assertThat((result as StreamEvent.Error).message).contains("Parse error")
    }

    @Test
    fun `ignores on_chat_model_end event`() {
        val event = SseEvent(
            event = "",
            data = """{"event":"on_chat_model_end","data":{"id":"step_1"}}""",
        )
        assertThat(mapper.map(event)).isNull()
    }

    @Test
    fun `ignores on_run_step_delta event`() {
        val event = SseEvent(
            event = "",
            data = """{"event":"on_run_step_delta","data":{"id":"step_1","delta":{"args":"partial"}}}""",
        )
        assertThat(mapper.map(event)).isNull()
    }
}
