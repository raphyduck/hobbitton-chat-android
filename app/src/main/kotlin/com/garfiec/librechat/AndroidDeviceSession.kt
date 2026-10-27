package com.garfiec.librechat

import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import coil3.SingletonImageLoader
import com.garfiec.librechat.core.common.di.AppThreads
import com.garfiec.librechat.core.data.engine.DeviceSession
import com.garfiec.librechat.core.logging.DiagnosticLogRepository
import com.garfiec.librechat.push.PushService
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [DeviceSession] on Android. Signed in: [PushService] starts. Signed out: the service stops (its
 * topic stays valid on ntfy, so a stream left open would keep notifying a signed-out phone), then
 * the `attachments/` cache directory (the files opened from conversations and the shared diagnostic
 * log), Coil's memory and disk caches and the diagnostic log's own segments go, each best effort:
 * a phone handed over after « Se déconnecter » used to keep every PDF and photo it had shown.
 */
class AndroidDeviceSession(
    private val context: Context,
    private val diagnostics: DiagnosticLogRepository,
) : DeviceSession {

    override fun onSignedIn() = PushService.start(context)

    override suspend fun onSignedOut() = withContext(AppThreads.io) {
        runCatching { context.stopService(Intent(context, PushService::class.java)) }
            .onFailure { Logger.w(it) { "Sign-out: the push service could not be stopped" } }
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
