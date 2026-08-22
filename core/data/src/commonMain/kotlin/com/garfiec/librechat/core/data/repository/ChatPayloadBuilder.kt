package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.chat.ChatProfile
import com.garfiec.librechat.core.model.request.AddedConversation
import com.garfiec.librechat.core.model.request.ChatRequest
import com.garfiec.librechat.core.model.request.EphemeralAgent
import com.garfiec.librechat.core.model.request.NO_PARENT
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object ChatPayloadBuilder {

    @OptIn(ExperimentalUuidApi::class)
    fun build(
        text: String,
        conversationId: String?,
        endpoint: String,
        endpointType: String? = null,
        key: String? = null,
        modelDisplayLabel: String? = null,
        model: String?,
        userMessageId: String? = null,
        parentMessageId: String? = null,
        agentId: String? = null,
        overrideParentMessageId: String? = null,
        responseMessageId: String? = null,
        isEdited: Boolean = false,
        isRegenerate: Boolean = false,
        isContinued: Boolean = false,
        webSearch: Boolean = false,
        files: List<FileReference>? = null,
        addedConvo: AddedConversation? = null,
        ephemeralAgent: EphemeralAgent? = null,
        isTemporary: Boolean = false,
    ): ChatRequest {
        val resolvedParentMessageId = parentMessageId ?: NO_PARENT

        return ChatRequest(
            text = text,
            conversationId = conversationId,
            messageId = userMessageId,
            parentMessageId = resolvedParentMessageId,
            endpoint = endpoint,
            endpointType = endpointType,
            key = key,
            modelDisplayLabel = modelDisplayLabel,
            model = model,
            agentId = agentId,
            overrideParentMessageId = overrideParentMessageId,
            responseMessageId = responseMessageId,
            isEdited = isEdited,
            isRegenerate = isRegenerate,
            isContinued = isContinued,
            webSearch = if (webSearch) true else null,
            files = files?.takeIf { it.isNotEmpty() },
            addedConvo = addedConvo,
            ephemeralAgent = ephemeralAgent,
            isTemporary = if (isTemporary) true else null,
            // v0.8.7 #13815: the user's IANA zone (e.g. "America/New_York") so the server resolves
            // agent {{current_date}}/{{current_datetime}} to the user's wall clock, not its own.
            // Additive — always sent; older servers ignore the unknown key.
            timezone = TimeZone.currentSystemDefault().id,
            // 0.8.8 #14344: idempotency key claimed by the server before job creation so a
            // replayed generation POST dedups to the original run instead of double-billing.
            // Minted here (once per send) and encoded once by `toBody`, so it stays byte-stable
            // across any transport-level retry of the same POST. Additive; ignored if unclaimed.
            clientRequestId = Uuid.random().toString(),
        )
    }

    /**
     * Folds the global profile into a request that is about to be sent.
     *
     * Applied at the single funnel every send goes through rather than at each call site: the Tasks
     * row that shipped invisible because one caller omitted one argument is a recent enough lesson.
     *
     * Two rules, both deliberate:
     *
     * - **The caller wins.** A request that already carries instructions or MCP servers keeps them;
     *   the profile only fills what is empty, and its servers are added to — never replace — the
     *   ones asked for. A profile that could silently drop a per-message choice would be worse than
     *   no profile.
     * - **Agents are left alone.** A real agent (`agentId`) carries its own instructions and tools,
     *   chosen when it was built. Layering a global prompt on top would put two systems of
     *   instruction in the same run, and the loser would be whichever the server reads second.
     */
    fun withProfile(request: ChatRequest, profile: ChatProfile): ChatRequest {
        if (profile.isEmpty || request.agentId != null) return request

        val servers = (request.ephemeralAgent?.mcp.orEmpty() + profile.mcpServers).distinct()
        return request.copy(
            promptPrefix = request.promptPrefix ?: profile.instructions.ifBlank { null },
            ephemeralAgent = when {
                servers.isEmpty() -> request.ephemeralAgent
                else -> (request.ephemeralAgent ?: EphemeralAgent()).copy(mcp = servers)
            },
        )
    }

    /**
     * Produces the final chat-send body. When [modelParams] is present, the provider-keyed model
     * params ride at the TOP LEVEL alongside the typed request fields — mirroring the web client's
     * `createPayload` spread `{ ...userMessage, ...endpointOption }`. [json] must be the shared
     * client instance (encodeDefaults=false / explicitNulls=false) so the encoded request keeps the
     * same wire shape as sending the [ChatRequest] directly. Params override base keys on collision.
     */
    fun toBody(json: Json, request: ChatRequest, modelParams: JsonObject?): JsonObject {
        val base = json.encodeToJsonElement(request).jsonObject
        return if (modelParams.isNullOrEmpty()) base else JsonObject(base + modelParams)
    }
}
