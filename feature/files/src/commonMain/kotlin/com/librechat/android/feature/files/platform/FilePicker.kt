package com.librechat.android.feature.files.platform

import androidx.compose.runtime.Composable

/**
 * Platform file picker composable.
 * When [launch] is called, shows the platform's native file picker.
 * On file selection, calls [onFilePicked] with a platform-specific file reference
 * (Android Uri, iOS NSURL).
 */
@Composable
expect fun rememberFilePickerLauncher(
    onFilePicked: (fileRef: Any) -> Unit,
): FilePickerLauncher

expect class FilePickerLauncher {
    fun launch(mimeType: String)
}
