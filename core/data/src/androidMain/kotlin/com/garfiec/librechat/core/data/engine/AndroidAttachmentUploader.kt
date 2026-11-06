package com.garfiec.librechat.core.data.engine

import android.content.ContentResolver
import android.net.Uri
import com.garfiec.librechat.core.network.api.SchedulerApi
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The phone's side of the deposit: a ticket from the scheduler, then the bytes streamed from the
 * content resolver straight to the ticket's URL, never held whole in memory (a video is tens of
 * megabytes). The upload host is not the scheduler's, so the portal's client, scoped to its own
 * host, does not serve here: a plain connection does, the ticket itself being the credential
 * (single use, a quarter of an hour).
 */
class AndroidAttachmentUploader(
    private val api: SchedulerApi,
    private val resolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher,
) : AttachmentUploader {

    override suspend fun upload(
        session: String?,
        filename: String?,
        mime: String,
        sizeBytes: Long,
        uri: String,
    ): UploadedAttachment {
        val ticket = api.uploadTicket(session, filename, mime, sizeBytes)
        withContext(ioDispatcher) { put(ticket.url, mime, sizeBytes, Uri.parse(uri)) }
        return UploadedAttachment(ticket.path)
    }

    private fun put(url: String, mime: String, sizeBytes: Long, uri: Uri) {
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", mime.ifBlank { "application/octet-stream" })
            setFixedLengthStreamingMode(sizeBytes)
        }
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("attachment unreadable: $uri")
            input.use { source ->
                connection.outputStream.use { sink ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        sink.write(buffer, 0, read)
                    }
                }
            }
            val code = connection.responseCode
            if (code !in HTTP_OK_RANGE) throw IOException("upload refused: HTTP $code")
            val answer = connection.inputStream.bufferedReader().use { it.readText() }
            val expected = digest.digest().joinToString("") { "%02x".format(it) }
            // The server answers with the sha256 of what it wrote: a mismatch is a corrupt copy.
            if ("sha256" in answer && expected !in answer) throw IOException("upload corrupted: digest differs")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 120_000
        const val BUFFER_BYTES = 64 * 1024
        val HTTP_OK_RANGE = 200..299
    }
}
