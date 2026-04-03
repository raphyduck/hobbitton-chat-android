package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

@Serializable
data class FileUploadConfig(
    val fileLimit: Int? = null,
    val fileSizeLimit: Long? = null,
    val totalSizeLimit: Long? = null,
    val supportedMimeTypes: List<String> = emptyList(),
    val disabled: Boolean = false,
)
