package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.Preset

interface PresetRepository {
    suspend fun getAll(): Result<List<Preset>>
    suspend fun create(preset: Preset): Result<Preset>
    suspend fun update(preset: Preset): Result<Preset>
    suspend fun delete(presetId: String): Result<Unit>
}
