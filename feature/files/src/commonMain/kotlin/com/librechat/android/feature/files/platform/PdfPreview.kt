package com.librechat.android.feature.files.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.librechat.android.feature.files.FilePreviewDisplayData

/**
 * Platform-specific PDF preview composable.
 * Android: Uses PdfRenderer to render pages as bitmaps.
 * iOS: Stub placeholder for now.
 */
@Composable
expect fun PdfPreview(
    file: FilePreviewDisplayData,
    onDownloadFile: (suspend (fileId: String, userId: String?) -> ByteArray?)?,
    modifier: Modifier = Modifier,
)
