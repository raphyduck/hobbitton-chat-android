package com.librechat.android.feature.settings.util

interface ContentReader {
    fun readBytes(uri: Any): ByteArray?
}
