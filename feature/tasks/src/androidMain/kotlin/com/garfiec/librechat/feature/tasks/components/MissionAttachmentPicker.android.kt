package com.garfiec.librechat.feature.tasks.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.garfiec.librechat.feature.tasks.util.StagedAttachment
import java.io.ByteArrayOutputStream
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The system photo picker — photos and, since 02/10/2026, videos — feeding [StagedAttachment]s. A
 * photo arrives already shrunk to what a vision model reads; a video as it is, within
 * [MAX_FILE_BYTES].
 *
 * Decode and re-encode happen off the main thread: a 12 MB camera photo takes long enough to
 * decode that doing it in the result callback would freeze the composer mid-tap.
 */
@Composable
internal actual fun rememberMissionAttachmentPicker(
    onPick: (List<StagedAttachment>) -> Unit,
): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val staged = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> context.stage(uri) }
            }
            if (staged.isNotEmpty()) onPick(staged)
        }
    }
    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
}

/**
 * The system file picker: any document, any type (02/10/2026). A picture picked here is shrunk as
 * one from the photo picker is; everything else travels as it is, within [MAX_FILE_BYTES].
 */
@Composable
internal actual fun rememberMissionFilePicker(
    onPick: (List<StagedAttachment>) -> Unit,
): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val staged = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> context.stage(uri) }
            }
            if (staged.isNotEmpty()) onPick(staged)
        }
    }
    return { launcher.launch(arrayOf("*/*")) }
}

/** A picture is shrunk ([stageImage]); anything else is read as it is ([stageFile]). */
private fun Context.stage(uri: Uri): StagedAttachment? {
    val mime = contentResolver.getType(uri) ?: "application/octet-stream"
    return if (mime.startsWith("image/") && mime != "image/gif") stageImage(uri) else stageFile(uri, mime)
}

/**
 * Reads one file whole, with its own name and type.
 *
 * Null past [MAX_FILE_BYTES], or on any failure: the engine has no upload route, a file rides
 * inside the message as base64, and a file that cannot travel becomes no chip rather than a send
 * that fails on bytes nobody can see.
 */
@OptIn(ExperimentalUuidApi::class)
private fun Context.stageFile(uri: Uri, mime: String): StagedAttachment? = runCatching {
    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
    val bytes = contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_FILE_BYTES) return null
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    } ?: return null
    StagedAttachment(id = Uuid.random().toString(), mime = mime, filename = name, bytes = bytes)
}.getOrNull()

/**
 * Reads and downsamples one image to at most [MAX_DIMENSION_PX] a side, re-encoded as JPEG.
 *
 * The ceiling is the vision models' own: past ~1.5 k pixels a side the provider shrinks the image
 * itself, so bigger bytes ride the whole way — into the HTTP body, into the transcript the app
 * re-fetches, into the context billed every turn — and buy nothing. JPEG rather than the original
 * container because the goal is small; a screenshot's transparency flattens to white, which is
 * what every chat app does to it too.
 *
 * Null on any failure: a photo that cannot be read becomes no chip at all rather than a chip that
 * would fail at send time with a message about bytes nobody chose.
 */
@OptIn(ExperimentalUuidApi::class)
private fun Context.stageImage(uri: Uri): StagedAttachment? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (
        bounds.outWidth / (sample * 2) >= MAX_DIMENSION_PX ||
        bounds.outHeight / (sample * 2) >= MAX_DIMENSION_PX
    ) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return null

    val bytes = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        out.toByteArray()
    }
    bitmap.recycle()
    StagedAttachment(
        id = Uuid.random().toString(),
        mime = "image/jpeg",
        filename = null,
        bytes = bytes,
    )
}.getOrNull()

private const val MAX_DIMENSION_PX = 1568
private const val JPEG_QUALITY = 82

/**
 * The largest file that travels: base64 adds a third, and the portal's edge refuses bodies past
 * 25 MB (hobbitton-proxy.inc).
 */
private const val MAX_FILE_BYTES = 16L * 1024 * 1024
private const val BUFFER_BYTES = 64 * 1024
