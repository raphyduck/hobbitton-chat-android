package com.garfiec.librechat

import android.content.Context
import co.touchlab.kermit.Logger
import coil3.SingletonImageLoader
import com.garfiec.librechat.core.data.engine.DeviceCleanup
import com.garfiec.librechat.core.logging.DiagnosticLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [DeviceCleanup] on Android: the `attachments/` cache directory (the files opened from
 * conversations and the shared diagnostic log), Coil's memory and disk caches, the diagnostic
 * log's own segments. Best effort, each on its own: a phone handed over after « Se déconnecter »
 * used to keep every PDF and photo it had shown.
 */
class AndroidDeviceCleanup(
    private val context: Context,
    private val diagnostics: DiagnosticLogRepository,
) : DeviceCleanup {

    override suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { File(context.cacheDir, ATTACHMENTS_DIR).deleteRecursively() }
            .onFailure { Logger.w(it) { "Sign-out: the opened attachments could not be deleted" } }
        runCatching {
            SingletonImageLoader.get(context).let { loader ->
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            }
        }.onFailure { Logger.w(it) { "Sign-out: the image cache could not be cleared" } }
        runCatching { diagnostics.clear() }
            .onFailure { Logger.w(it) { "Sign-out: the diagnostic log could not be cleared" } }
        Unit
    }

    private companion object {
        /** Where `AttachmentOpener` and `AndroidDiagnosticLogShare` write. */
        const val ATTACHMENTS_DIR = "attachments"
    }
}
