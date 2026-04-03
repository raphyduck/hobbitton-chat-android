package com.garfiec.librechat.feature.agents.util

import android.content.Context
import android.net.Uri

class AndroidContentReader(private val context: Context) : ContentReader {

    override fun readBytes(uri: Any): ByteArray? {
        val androidUri = uri as? Uri ?: return null
        return context.contentResolver.openInputStream(androidUri)?.use { it.readBytes() }
    }

    override fun getMimeType(uri: Any): String? {
        val androidUri = uri as? Uri ?: return null
        return context.contentResolver.getType(androidUri)
    }
}
