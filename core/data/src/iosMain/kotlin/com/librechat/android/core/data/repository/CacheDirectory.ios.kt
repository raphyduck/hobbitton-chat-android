package com.librechat.android.core.data.repository

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager

@OptIn(ExperimentalForeignApi::class)
internal actual fun deleteDirectoryRecursively(path: String) {
    val fm = NSFileManager.defaultManager
    if (fm.fileExistsAtPath(path)) {
        fm.removeItemAtPath(path, null)
    }
}
