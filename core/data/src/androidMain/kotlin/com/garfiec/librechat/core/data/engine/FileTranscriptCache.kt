package com.garfiec.librechat.core.data.engine

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * [TranscriptCache] as gzip files in the app's cache directory (Android may clear it under
 * pressure, and nothing is lost: the engine has the originals). One file per session, named after
 * it; a read touches the file, and after each write the oldest files go until the directory holds
 * at most [maxEntries] files and [maxBytes] in all. A tail too large once compressed ([maxEntryBytes])
 * is not kept: it would evict a dozen others for one conversation. JSON gzips five to ten times:
 * the heaviest tail measured (1.8 MB) fits in a few hundred kilobytes.
 */
class FileTranscriptCache(
    private val directory: File,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
    private val maxEntries: Int = MAX_ENTRIES,
    private val maxBytes: Long = MAX_BYTES,
    private val maxEntryBytes: Long = MAX_ENTRY_BYTES,
) : TranscriptCache {

    override suspend fun read(sessionId: String): CachedTranscript? = withContext(ioDispatcher) {
        val file = fileOf(sessionId)
        if (!file.isFile) return@withContext null
        runCatching {
            val text = GZIPInputStream(file.inputStream().buffered()).bufferedReader().use { it.readText() }
            json.decodeFromString(CachedTranscript.serializer(), text).also { file.setLastModified(System.currentTimeMillis()) }
        }.onFailure {
            Logger.w("TranscriptCache", it) { "unreadable tail of $sessionId dropped" }
            file.delete()
        }.getOrNull()
    }

    override suspend fun write(sessionId: String, transcript: CachedTranscript) = withContext(ioDispatcher) {
        runCatching {
            directory.mkdirs()
            val file = fileOf(sessionId)
            val fresh = File(directory, file.name + ".tmp")
            GZIPOutputStream(fresh.outputStream().buffered()).bufferedWriter().use {
                it.write(json.encodeToString(CachedTranscript.serializer(), transcript))
            }
            if (fresh.length() > maxEntryBytes) {
                fresh.delete()
                file.delete()
            } else {
                fresh.renameTo(file)
            }
            trim()
        }.onFailure { Logger.w("TranscriptCache", it) { "tail of $sessionId not kept" } }
        Unit
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        directory.listFiles()?.forEach { it.delete() }
        Unit
    }

    /** Oldest first out, until the directory is within its two bounds. */
    private fun trim() {
        val files = directory.listFiles { f -> f.isFile && !f.name.endsWith(".tmp") }?.sortedBy { it.lastModified() } ?: return
        var count = files.size
        var bytes = files.sumOf { it.length() }
        for (file in files) {
            if (count <= maxEntries && bytes <= maxBytes) break
            bytes -= file.length()
            count--
            file.delete()
        }
    }

    private fun fileOf(sessionId: String): File = File(directory, sessionId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json.gz")

    private companion object {
        const val MAX_ENTRIES = 40
        const val MAX_BYTES = 12L * 1024 * 1024
        const val MAX_ENTRY_BYTES = 2L * 1024 * 1024
    }
}
