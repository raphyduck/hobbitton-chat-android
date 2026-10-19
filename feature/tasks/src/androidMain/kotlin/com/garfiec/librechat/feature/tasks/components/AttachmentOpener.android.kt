package com.garfiec.librechat.feature.tasks.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import co.touchlab.kermit.Logger
import com.garfiec.librechat.feature.tasks.util.ChatPart
import java.io.File
import kotlin.io.encoding.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The file's bytes come out of its `data:` URL into the app's cache (`attachments/`, shared through
 * the `:app`'s FileProvider, authority `<package>.files`), then go to whatever opens that type. The
 * viewer saves or shares it; nothing is written outside the cache.
 */
@Composable
internal actual fun rememberAttachmentOpener(): ((ChatPart.Attachment) -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return { attachment ->
        scope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { cached(context, attachment) }.getOrNull() }
            if (file == null) {
                Toast.makeText(context, attachment.filename ?: attachment.mime, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, attachment.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                context.startActivity(Intent.createChooser(view, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                Logger.w(e) { "No viewer for ${attachment.mime}" }
            }
        }
    }
}

private fun cached(context: Context, attachment: ChatPart.Attachment): File {
    val base64 = attachment.dataUrl.substringAfter("base64,", missingDelimiterValue = "")
    require(base64.isNotEmpty()) { "not a base64 data URL" }
    val directory = File(context.cacheDir, ATTACHMENTS_DIR).apply { mkdirs() }
    return File(directory, fileName(attachment)).apply { writeBytes(Base64.Mime.decode(base64)) }
}

/** The file's own name, or one made from its type; never a path out of the directory. */
private fun fileName(attachment: ChatPart.Attachment): String {
    val given = attachment.filename?.substringAfterLast('/')?.replace(Regex("[^A-Za-z0-9._ -]"), "_")
    if (!given.isNullOrBlank() && given != "." && given != "..") return given
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(attachment.mime) ?: "bin"
    return "piece-${attachment.id.hashCode().toUInt()}.$extension"
}

private const val ATTACHMENTS_DIR = "attachments"
