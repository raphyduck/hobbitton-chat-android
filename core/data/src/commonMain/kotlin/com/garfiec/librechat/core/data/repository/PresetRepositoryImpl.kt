package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.network.api.PresetsApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PresetRepositoryImpl(
    private val presetsApi: PresetsApi,
) : PresetRepository {

    private val cacheMutex = Mutex()
    private var cachedPresets: List<Preset>? = null

    override suspend fun getAll(): Result<List<Preset>> {
        return safeApiCall {
            cacheMutex.withLock {
                cachedPresets?.let { return@withLock it }
                val presets = presetsApi.getPresets()
                cachedPresets = presets
                presets
            }
        }
    }

    override suspend fun create(preset: Preset): Result<Preset> {
        return safeApiCall {
            val created = presetsApi.createPreset(preset)
            cacheMutex.withLock { cachedPresets = null }
            created
        }
    }

    override suspend fun update(preset: Preset): Result<Preset> {
        return safeApiCall {
            val updated = presetsApi.updatePreset(preset)
            cacheMutex.withLock { cachedPresets = null }
            updated
        }
    }

    override suspend fun delete(presetId: String): Result<Unit> {
        return safeApiCall {
            presetsApi.deletePreset(presetId)
            cacheMutex.withLock { cachedPresets = null }
        }
    }
}
