package com.garfiec.librechat.diagnostics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.garfiec.librechat.core.logging.DiagnosticLogRepository
import com.garfiec.librechat.core.logging.DiagnosticLogShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The diagnostic log through the phone's share sheet (20/10/2026): written to the app's cache
 * (`attachments/`, the FileProvider the conversation's files already go through, authority
 * `<package>.files`), then `ACTION_SEND` as text, so a mail or a message carries it. Nothing is
 * written outside the cache; the buffer itself is redacted at write time.
 */
class AndroidDiagnosticLogShare(
    private val context: Context,
    private val repository: DiagnosticLogRepository,
) : DiagnosticLogShare {

    override suspend fun share() {
        val file = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, ATTACHMENTS_DIR).apply { mkdirs() }
            File(directory, FILE_NAME).apply { writeText(repository.exportText()) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // From the application context, so the chooser needs its own task.
        context.startActivity(Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val ATTACHMENTS_DIR = "attachments"
        const val FILE_NAME = "eva-diagnostic.jsonl"
    }
}
