package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.StartupConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Result of a backend version compatibility check.
 */
data class VersionCheckResult(
    /** The version detected on the backend, or null if it could not be determined. */
    val backendVersion: String?,
    /** The version this app was built for. */
    val supportedVersion: String,
    /** Whether the versions are compatible (same major.minor). */
    val isCompatible: Boolean,
)

interface ConfigRepository {
    val startupConfig: StateFlow<StartupConfig?>
    val endpointConfigs: StateFlow<Map<String, EndpointConfig>>
    val availableModels: StateFlow<Map<String, List<String>>>
    suspend fun validateServerUrl(url: String): Result<StartupConfig>
    suspend fun fetchStartupConfig(): Result<StartupConfig>
    suspend fun fetchEndpoints(): Result<Map<String, EndpointConfig>>
    suspend fun fetchModels(): Result<Map<String, List<String>>>

    /**
     * Checks the backend version against this app's supported version.
     * Returns a [VersionCheckResult] on success, or an error if the check fails.
     *
     * The check is best-effort: if the backend does not expose its version,
     * the result will have [VersionCheckResult.backendVersion] = null and
     * [VersionCheckResult.isCompatible] = true (fail-open).
     */
    suspend fun checkBackendVersion(): Result<VersionCheckResult>
}
