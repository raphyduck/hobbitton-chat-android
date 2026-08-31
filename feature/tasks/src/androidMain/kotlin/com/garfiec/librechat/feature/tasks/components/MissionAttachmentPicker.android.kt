package com.garfiec.librechat.feature.tasks.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
 * The system photo picker, feeding [StagedAttachment]s already shrunk to what a vision model reads.
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
                uris.mapNotNull { uri -> context.stageImage(uri) }
            }
            if (staged.isNotEmpty()) onPick(staged)
        }
    }
    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

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
