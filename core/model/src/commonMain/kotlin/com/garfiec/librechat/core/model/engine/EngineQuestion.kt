package com.garfiec.librechat.core.model.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A question the agent put to the person, waiting for an answer: OpenCode's `question` tool.
 *
 * The tool **blocks the turn**: the engine keeps the request in memory, announces it on the feed
 * (`question.asked`), and the run resumes only once `POST /question/{id}/reply` or
 * `POST /question/{id}/reject` names it. Nothing else unblocks it; a session left with an
 * unanswered question stays « running » for good. Read in the running engine's code (1.18.21,
 * `question/index.ts` and `tool/question.ts`) and in its OpenAPI (`QuestionRequest`), not assumed.
 *
 * One request carries one or more questions, answered together: the reply is one list of labels per
 * question, in order.
 */
@Serializable
data class EngineQuestionRequest(
    /** `que…`: what the reply and the reject routes name. */
    val id: String,
    @SerialName("sessionID") val sessionId: String,
    val questions: List<EngineQuestionInfo> = emptyList(),
    /** The tool call that asked, when the engine says so. */
    val tool: EngineQuestionTool? = null,
)

/**
 * One question. The model writes it: [header] is a short label (30 characters at most, by the tool's
 * own schema), [question] the full sentence.
 *
 * [custom] defaults to **true** on the engine's side: the tool's description tells the model that
 * a « type your own answer » field is added automatically, so it never offers an « Other » option of
 * its own. A form that drops the free field leaves the person with only the model's guesses.
 */
@Serializable
data class EngineQuestionInfo(
    val question: String,
    val header: String = "",
    val options: List<EngineQuestionOption> = emptyList(),
    /** Several options may be picked; the answer then carries each label. */
    val multiple: Boolean = false,
    val custom: Boolean = true,
)

@Serializable
data class EngineQuestionOption(
    val label: String,
    val description: String = "",
)

@Serializable
data class EngineQuestionTool(
    @SerialName("messageID") val messageId: String,
    @SerialName("callID") val callId: String,
)

/**
 * `POST /question/{id}/reply`: one list of labels per question, in the order they were asked. A
 * typed answer travels as a label of its own; the engine hands the model the strings verbatim
 * (`"question"="label, label"`), it does not check them against the options.
 */
@Serializable
data class EngineQuestionReply(
    val answers: List<List<String>>,
)
