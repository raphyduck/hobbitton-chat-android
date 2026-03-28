package com.librechat.android.feature.files.screen

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.librechat.android.feature.files.FilePreviewDisplayData
import com.librechat.android.feature.files.R
import timber.log.Timber
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewDialog(
    file: FilePreviewDisplayData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDownloadFile: (suspend (fileId: String, userId: String?) -> ByteArray?)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = file.filename,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_close_preview),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            when {
                file.type.startsWith("image/") -> {
                    ImagePreview(
                        url = file.previewUrl,
                        filename = file.filename,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
                file.type == "application/pdf" -> {
                    PdfPreview(
                        file = file,
                        onDownloadFile = onDownloadFile,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
                file.type.startsWith("text/") || file.type == "application/json" ||
                    file.type == "application/xml" || file.type == "application/javascript" -> {
                    TextContentPreview(
                        file = file,
                        onDownloadFile = onDownloadFile,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
                else -> {
                    FileInfoCard(
                        file = file,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePreview(
    url: String?,
    filename: String,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offset = Offset(
            x = offset.x + panChange.x,
            y = offset.y + panChange.y,
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            Timber.d("ImagePreview loading URL: %s", url)
            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.preview_of, filename),
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Loading ->
                            Timber.d("ImagePreview: loading %s", url)
                        is AsyncImagePainter.State.Success ->
                            Timber.d("ImagePreview: success %s", url)
                        is AsyncImagePainter.State.Error ->
                            Timber.e(
                                state.result.throwable,
                                "ImagePreview: error loading %s",
                                url,
                            )
                        is AsyncImagePainter.State.Empty ->
                            Timber.d("ImagePreview: empty state for %s", url)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .transformable(state = transformableState),
                contentScale = ContentScale.Fit,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.image_preview_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// -- PDF Preview --

private sealed interface PdfLoadState {
    data object Loading : PdfLoadState
    data class Success(val pages: List<Bitmap>) : PdfLoadState
    data class Error(val message: String) : PdfLoadState
}

@Composable
private fun PdfPreview(
    file: FilePreviewDisplayData,
    onDownloadFile: (suspend (fileId: String, userId: String?) -> ByteArray?)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var loadState by remember { mutableStateOf<PdfLoadState>(PdfLoadState.Loading) }
    var tempFile by remember { mutableStateOf<File?>(null) }

    // Clean up temp file when composable leaves composition
    DisposableEffect(file.fileId) {
        onDispose {
            tempFile?.delete()
            // Recycle bitmaps
            val currentState = loadState
            if (currentState is PdfLoadState.Success) {
                currentState.pages.forEach { bitmap ->
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    // Download and render PDF
    LaunchedEffect(file.fileId) {
        loadState = PdfLoadState.Loading
        try {
            val bytes = onDownloadFile?.invoke(file.fileId, file.userId)
            if (bytes == null) {
                loadState = PdfLoadState.Error(context.getString(R.string.failed_to_download_pdf))
                return@LaunchedEffect
            }

            // Write to temp file
            val pdfTempFile = File(context.cacheDir, "pdf_preview_${file.fileId}.pdf")
            pdfTempFile.writeBytes(bytes)
            tempFile = pdfTempFile

            // Render pages with PdfRenderer
            val fd = ParcelFileDescriptor.open(
                pdfTempFile,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            val renderer = PdfRenderer(fd)
            val pageCount = renderer.pageCount
            Timber.d("PdfPreview: opened PDF with %d pages", pageCount)

            // Render up to 50 pages to avoid memory issues on very large PDFs
            val maxPages = minOf(pageCount, 50)
            val bitmaps = mutableListOf<Bitmap>()

            // Calculate a reasonable width based on screen density
            val displayMetrics = context.resources.displayMetrics
            val targetWidth = displayMetrics.widthPixels

            for (i in 0 until maxPages) {
                val page = renderer.openPage(i)
                // Scale to fill screen width while maintaining aspect ratio
                val scale = targetWidth.toFloat() / page.width
                val bitmapWidth = targetWidth
                val bitmapHeight = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(
                    bitmapWidth,
                    bitmapHeight,
                    Bitmap.Config.ARGB_8888,
                )
                // Render with white background
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                )
                page.close()
                bitmaps.add(bitmap)
            }

            renderer.close()
            fd.close()

            loadState = PdfLoadState.Success(bitmaps)
            if (pageCount > maxPages) {
                Timber.d("PdfPreview: showing first %d of %d pages", maxPages, pageCount)
            }
        } catch (e: Exception) {
            Timber.e(e, "PdfPreview: failed to render PDF")
            loadState = PdfLoadState.Error(
                e.message ?: context.getString(R.string.failed_to_render_pdf),
            )
        }
    }

    when (val state = loadState) {
        is PdfLoadState.Loading -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.loading_pdf),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        is PdfLoadState.Error -> {
            PdfErrorFallback(
                file = file,
                errorMessage = state.message,
                modifier = modifier,
            )
        }
        is PdfLoadState.Success -> {
            PdfPagesList(
                pages = state.pages,
                filename = file.filename,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun PdfPagesList(
    pages: List<Bitmap>,
    filename: String,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offset = Offset(
            x = offset.x + panChange.x,
            y = offset.y + panChange.y,
        )
    }

    LazyColumn(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .transformable(state = transformableState),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(
            items = pages,
            key = { index, _ -> "page_$index" },
        ) { index, bitmap ->
            Column {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.page_cd, index + 1, filename),
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
                // Page number indicator
                Text(
                    text = stringResource(R.string.page_of, index + 1, pages.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PdfErrorFallback(
    file: FilePreviewDisplayData,
    errorMessage: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error,
                )

                Text(
                    text = file.filename,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = stringResource(R.string.could_not_render_pdf),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoRow(stringResource(R.string.info_type), file.type)
                    InfoRow(stringResource(R.string.info_size), file.formattedSize)
                    file.createdAt?.let { InfoRow(stringResource(R.string.info_created), it) }
                    file.source?.let { InfoRow(stringResource(R.string.info_source), it) }
                }
            }
        }
    }
}

// -- Text Content Preview --

private sealed interface TextLoadState {
    data object Loading : TextLoadState
    data class Success(val content: String) : TextLoadState
    data class Error(val message: String) : TextLoadState
}

@Composable
private fun TextContentPreview(
    file: FilePreviewDisplayData,
    onDownloadFile: (suspend (fileId: String, userId: String?) -> ByteArray?)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var loadState by remember { mutableStateOf<TextLoadState>(TextLoadState.Loading) }

    LaunchedEffect(file.fileId) {
        loadState = TextLoadState.Loading
        try {
            val bytes = onDownloadFile?.invoke(file.fileId, file.userId)
            if (bytes == null) {
                loadState = TextLoadState.Error(context.getString(R.string.failed_to_download_file))
                return@LaunchedEffect
            }
            val content = bytes.decodeToString()
            // Limit to ~500KB of text to avoid UI performance issues
            val truncated = if (content.length > 500_000) {
                content.take(500_000) + context.getString(R.string.content_truncated)
            } else {
                content
            }
            loadState = TextLoadState.Success(truncated)
        } catch (e: Exception) {
            Timber.e(e, "TextContentPreview: failed to load text content")
            loadState = TextLoadState.Error(
                e.message ?: context.getString(R.string.failed_to_load_content),
            )
        }
    }

    when (val state = loadState) {
        is TextLoadState.Loading -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.loading_file_content),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        is TextLoadState.Error -> {
            TextErrorFallback(
                file = file,
                errorMessage = state.message,
                modifier = modifier,
            )
        }
        is TextLoadState.Success -> {
            val verticalScrollState = rememberScrollState()
            val horizontalScrollState = rememberScrollState()

            Column(
                modifier = modifier
                    .verticalScroll(verticalScrollState)
                    .padding(16.dp),
            ) {
                // File info header
                Text(
                    text = file.filename,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${file.type} - ${file.formattedSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable text content with monospace font
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState),
                ) {
                    Text(
                        text = state.content,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier.widthIn(min = 600.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TextErrorFallback(
    file: FilePreviewDisplayData,
    errorMessage: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error,
                )

                Text(
                    text = file.filename,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = stringResource(R.string.could_not_load_file_content),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoRow(stringResource(R.string.info_type), file.type)
                    InfoRow(stringResource(R.string.info_size), file.formattedSize)
                    file.createdAt?.let { InfoRow(stringResource(R.string.info_created), it) }
                    file.source?.let { InfoRow(stringResource(R.string.info_source), it) }
                }
            }
        }
    }
}

// -- Shared Components --

@Composable
private fun FileInfoCard(
    file: FilePreviewDisplayData,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = fileTypeIconLarge(file.type),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                Text(
                    text = file.filename,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoRow(stringResource(R.string.info_type), file.type)
                    InfoRow(stringResource(R.string.info_size), file.formattedSize)
                    file.createdAt?.let { InfoRow(stringResource(R.string.info_created), it) }
                    file.source?.let { InfoRow(stringResource(R.string.info_source), it) }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

private fun fileTypeIconLarge(type: String): ImageVector = when {
    type.startsWith("image/") -> Icons.Default.Image
    type.startsWith("video/") -> Icons.Default.VideoFile
    type.startsWith("audio/") -> Icons.Default.AudioFile
    type == "application/pdf" -> Icons.Default.PictureAsPdf
    type.startsWith("text/") || type == "application/json" -> Icons.Default.Description
    else -> Icons.Default.Description
}
