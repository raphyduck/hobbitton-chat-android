package com.librechat.android.feature.files.platform

import platform.Foundation.NSByteCountFormatter
import platform.Foundation.NSByteCountFormatterCountStyleFile

actual fun formatFileSize(bytes: Long): String {
    return NSByteCountFormatter.stringFromByteCount(
        byteCount = bytes,
        countStyle = NSByteCountFormatterCountStyleFile,
    )
}
