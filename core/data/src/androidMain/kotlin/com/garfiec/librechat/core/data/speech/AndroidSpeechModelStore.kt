package com.garfiec.librechat.core.data.speech

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.di.AppThreads
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [SpeechModelStore] over Android's DownloadManager (11/11/2026): the 671 MB come down in the
 * system's own service, over Wi-Fi only, resumed after a cut, with its notification, whether the
 * app is open or not. The files land as `.part` under the app's external files directory
 * ([ParakeetModel.DIRECTORY]), each one hashed once the last is down, then renamed; the model is
 * ready when every final file is there and the `ready` mark written after the check. A file that
 * does not hash to its published SHA-256 is thrown away with the rest.
 *
 * The download ids live in a small preferences file, so a download the app was killed during is
 * followed again at the next start. The state is read off the DownloadManager every second while a
 * download runs, on the app's IO threads.
 */
class AndroidSpeechModelStore(context: Context) : SpeechModelStore {
    private val app = context.applicationContext
    private val manager = app.getSystemService(DownloadManager::class.java)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + AppThreads.io)
    private val directory: File? get() = app.getExternalFilesDir(null)?.let { File(it, ParakeetModel.DIRECTORY) }

    private val _state = MutableStateFlow<SpeechModelState>(SpeechModelState.Absent)
    override val state: StateFlow<SpeechModelState> = _state.asStateFlow()

    private var follow: Job? = null

    override val supported: Boolean = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }

    init {
        scope.launch {
            _state.value = when {
                readyPaths() != null -> SpeechModelState.Ready(checkNotNull(readyPaths()))
                downloadIds().isNotEmpty() -> SpeechModelState.Downloading(0, ParakeetModel.TOTAL_BYTES).also { followDownload() }
                else -> SpeechModelState.Absent
            }
        }
    }

    override fun download() {
        scope.launch {
            if (_state.value is SpeechModelState.Ready || _state.value is SpeechModelState.Verifying) return@launch
            stopDownloads()
            val dir = directory ?: return@launch fail(SpeechModelFailure.STORAGE)
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            if (dir.usableSpace < ParakeetModel.TOTAL_BYTES + SPACE_MARGIN_BYTES) return@launch fail(SpeechModelFailure.STORAGE)
            val ids = ParakeetModel.FILES.map { file ->
                val request = DownloadManager.Request(Uri.parse(file.url))
                    .setTitle(DOWNLOAD_TITLE)
                    .setDescription(file.name)
                    .setDestinationInExternalFilesDir(app, null, "${ParakeetModel.DIRECTORY}/${file.name}$PART")
                    .setAllowedOverMetered(false)
                    .setAllowedOverRoaming(false)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                manager.enqueue(request)
            }
            prefs.edit().putString(KEY_IDS, ids.joinToString(",")).apply()
            Logger.i(tag = TAG) { "Speech model download started (${ParakeetModel.TOTAL_BYTES} bytes)" }
            _state.value = SpeechModelState.Downloading(0, ParakeetModel.TOTAL_BYTES)
            followDownload()
        }
    }

    override fun cancel() {
        scope.launch {
            stopDownloads()
            directory?.listFiles()?.forEach { it.delete() }
            _state.value = SpeechModelState.Absent
        }
    }

    override fun delete() {
        scope.launch {
            stopDownloads()
            directory?.deleteRecursively()
            Logger.i(tag = TAG) { "Speech model deleted" }
            _state.value = SpeechModelState.Absent
        }
    }

    private fun followDownload() {
        follow?.cancel()
        follow = scope.launch {
            while (isActive) {
                val ids = downloadIds()
                if (ids.isEmpty()) return@launch
                val now = downloadState(ids.map(::statusOf), ParakeetModel.TOTAL_BYTES)
                when (now) {
                    is SpeechModelState.Verifying -> {
                        _state.value = now
                        verify(ids)
                        return@launch
                    }
                    is SpeechModelState.Failed -> {
                        stopDownloads()
                        directory?.listFiles()?.forEach { it.delete() }
                        fail(now.failure)
                        return@launch
                    }
                    else -> _state.value = now
                }
                delay(POLL_MS)
            }
        }
    }

    /** Every file is down: each is hashed, renamed, then the mark is written. */
    private fun verify(ids: List<Long>) {
        val dir = directory ?: return fail(SpeechModelFailure.STORAGE)
        val bad = ParakeetModel.FILES.firstOrNull { file ->
            val part = File(dir, file.name + PART)
            !part.isFile || part.length() != file.bytes || sha256(part) != file.sha256
        }
        // The entries go first: removing one deletes the file at the path it downloaded to.
        prefs.edit().remove(KEY_IDS).apply()
        if (bad != null) {
            Logger.w(tag = TAG) { "Speech model file ${bad.name} did not match its SHA-256: thrown away" }
            runCatching { manager.remove(*ids.toLongArray()) }
            dir.listFiles()?.forEach { it.delete() }
            return fail(SpeechModelFailure.CORRUPT)
        }
        ParakeetModel.FILES.forEach { file -> File(dir, file.name + PART).renameTo(File(dir, file.name)) }
        runCatching { manager.remove(*ids.toLongArray()) }
        File(dir, READY_MARK).writeText(ParakeetModel.FILES.joinToString("\n") { it.sha256 })
        val paths = readyPaths()
        _state.value = if (paths != null) SpeechModelState.Ready(paths) else SpeechModelState.Failed(SpeechModelFailure.STORAGE)
        Logger.i(tag = TAG) { "Speech model ready" }
    }

    private fun statusOf(id: Long): Pair<FileDownload, Long> {
        val query = DownloadManager.Query().setFilterById(id)
        return manager.query(query).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) return@use FileDownload.MISSING to 0L
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            fileDownload(status, reason) to done.coerceAtLeast(0)
        }
    }

    private fun stopDownloads() {
        follow?.cancel()
        follow = null
        val ids = downloadIds()
        prefs.edit().remove(KEY_IDS).apply()
        if (ids.isNotEmpty()) runCatching { manager.remove(*ids.toLongArray()) }
    }

    private fun fail(failure: SpeechModelFailure) {
        Logger.w(tag = TAG) { "Speech model download failed: $failure" }
        _state.value = SpeechModelState.Failed(failure)
    }

    private fun downloadIds(): List<Long> =
        prefs.getString(KEY_IDS, null).orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }

    /** The final files, when the mark says they were checked and they are all still there. */
    private fun readyPaths(): SpeechModelPaths? {
        val dir = directory ?: return null
        if (!File(dir, READY_MARK).isFile) return null
        val present = ParakeetModel.FILES.all { File(dir, it.name).length() == it.bytes }
        if (!present) return null
        fun path(file: SpeechModelFile) = File(dir, file.name).absolutePath
        return SpeechModelPaths(
            encoder = path(ParakeetModel.ENCODER),
            decoder = path(ParakeetModel.DECODER),
            joiner = path(ParakeetModel.JOINER),
            tokens = path(ParakeetModel.TOKENS),
            vad = path(ParakeetModel.VAD),
        )
    }

    private companion object {
        const val TAG = "SpeechModel"
        const val PREFS = "eva_speech_model"
        const val KEY_IDS = "download_ids"
        const val PART = ".part"
        const val READY_MARK = "ready"
        const val POLL_MS = 1_000L
        const val SPACE_MARGIN_BYTES = 100L * 1024 * 1024
        const val DOWNLOAD_TITLE = "Eva · modèle de dictée"
    }
}

/** The DownloadManager's status and reason, as the model's download reads them. */
internal fun fileDownload(status: Int, reason: Int): FileDownload = when (status) {
    DownloadManager.STATUS_SUCCESSFUL -> FileDownload.DONE
    DownloadManager.STATUS_RUNNING -> FileDownload.RUNNING
    DownloadManager.STATUS_PENDING -> FileDownload.PENDING
    DownloadManager.STATUS_PAUSED ->
        if (reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI || reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK) {
            FileDownload.WAITING_FOR_WIFI
        } else {
            FileDownload.PENDING
        }
    DownloadManager.STATUS_FAILED ->
        if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE || reason == DownloadManager.ERROR_DEVICE_NOT_FOUND) {
            FileDownload.FAILED_STORAGE
        } else {
            FileDownload.FAILED_NETWORK
        }
    else -> FileDownload.PENDING
}

/** A file's SHA-256 in lowercase hex, read in 1 MB blocks. */
internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 20)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
