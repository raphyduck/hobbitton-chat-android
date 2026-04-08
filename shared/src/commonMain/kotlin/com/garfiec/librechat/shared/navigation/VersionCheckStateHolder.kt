package com.garfiec.librechat.shared.navigation

import androidx.compose.runtime.Immutable
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Immutable
data class VersionMismatchState(
    val supportedVersion: String,
    val backendVersion: String,
)

class VersionCheckStateHolder(
    private val configRepository: ConfigRepository,
    private val settingsDataStore: SettingsDataStore,
    private val scope: CoroutineScope,
) {

    private val _versionMismatch = MutableStateFlow<VersionMismatchState?>(null)
    val versionMismatch: StateFlow<VersionMismatchState?> = _versionMismatch.asStateFlow()

    fun checkBackendVersion() {
        scope.launch {
            when (val result = configRepository.checkBackendVersion()) {
                is Result.Success -> {
                    val checkResult = result.data
                    val detectedVersion = checkResult.backendVersion
                    if (!checkResult.isCompatible && detectedVersion != null) {
                        val dismissedVersion = settingsDataStore.dismissedVersionWarning.first()
                        if (dismissedVersion != detectedVersion) {
                            _versionMismatch.value = VersionMismatchState(
                                supportedVersion = checkResult.supportedVersion,
                                backendVersion = detectedVersion,
                            )
                        }
                    }
                }
                is Result.Error -> {
                    Logger.w(result.exception) { "Failed to check backend version: ${result.message}" }
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun dismissVersionWarning() {
        _versionMismatch.value = null
    }

    fun dismissVersionWarningPermanently() {
        val backendVersion = _versionMismatch.value?.backendVersion
        _versionMismatch.value = null
        if (backendVersion != null) {
            scope.launch {
                settingsDataStore.setDismissedVersionWarning(backendVersion)
            }
        }
    }
}
