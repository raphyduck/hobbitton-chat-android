package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.result.safeApiCall
import com.librechat.android.core.model.Preset
import com.librechat.android.core.network.api.PresetsApi

class PresetRepositoryImpl(
    private val presetsApi: PresetsApi,
) : PresetRepository {

    private var cachedPresets: List<Preset>? = null

    override suspend fun getAll(): Result<List<Preset>> {
        return safeApiCall {
            if (cachedPresets != null) return@safeApiCall cachedPresets!!
            val presets = presetsApi.getPresets()
            cachedPresets = presets
            presets
        }
    }

    override suspend fun create(preset: Preset): Result<Preset> {
        return safeApiCall {
            val created = presetsApi.createPreset(preset)
            cachedPresets = null
            created
        }
    }

    override suspend fun update(preset: Preset): Result<Preset> {
        return safeApiCall {
            val updated = presetsApi.updatePreset(preset)
            cachedPresets = null
            updated
        }
    }

    override suspend fun delete(presetId: String): Result<Unit> {
        return safeApiCall {
            presetsApi.deletePreset(presetId)
            cachedPresets = null
        }
    }
}
