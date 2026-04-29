package com.garfiec.librechat.core.data.endpoint

import com.garfiec.librechat.core.model.EModelEndpoint
import com.garfiec.librechat.core.model.EndpointConfig

data class EndpointDispatch(
    val endpointType: String?,
    val key: String?,
    val modelDisplayLabel: String?,
)

object EndpointClassifier {
    /**
     * Resolve the wire fields for a chat-send request, mirroring web's
     * getEndpointField(endpointsConfig, endpoint, 'type') + getExpiry() pattern.
     *
     * - endpointType: prefers EndpointConfig.type from /api/config; falls back to
     *   the endpoint name itself if it's a known built-in (covers cold-start
     *   race where /api/config hasn't loaded); otherwise "custom".
     * - key: "never" when the endpoint is user_provided (matches web's
     *   getExpiry() returning expiresAt || "never"). null otherwise so the
     *   field is omitted from the wire body. Time-limited keys are a follow-up.
     * - modelDisplayLabel: from config; falls back to the endpoint name.
     */
    fun classify(endpointName: String, configs: Map<String, EndpointConfig>): EndpointDispatch {
        val config = configs[endpointName]
        val endpointType = config?.type
            ?: endpointName.takeIf { it in EModelEndpoint.BUILT_IN_NAMES }
            ?: "custom"
        val key = if (config?.userProvide == true) "never" else null
        val modelDisplayLabel = config?.modelDisplayLabel ?: endpointName
        return EndpointDispatch(endpointType, key, modelDisplayLabel)
    }
}
