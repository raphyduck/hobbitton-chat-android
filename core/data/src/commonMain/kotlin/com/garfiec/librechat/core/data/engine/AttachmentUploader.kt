package com.garfiec.librechat.core.data.engine

/**
 * Deposits on the server a file that cannot ride inside a message (06/11/2026, Raphaël: « il faut
 * pouvoir ajouter des vidéos également »): a video, or any file past what a message carries.
 *
 * A photo travels as a base64 data URL in the prompt; a 50 MB video would not, and no model
 * watches it anyway. The file goes instead to the files connector's binary channel: the scheduler
 * opens a one-shot ticket (`POST /pieces-jointes/ticket`, under `eva/<session>/`), the phone PUTs
 * the bytes on its URL, and the message names the server path, which the tools take as it is
 * (a WhatsApp attachment, a NAS copy, an email).
 */
interface AttachmentUploader {
    /**
     * Uploads the file behind [uri] and returns where it landed. [session] is null for the first
     * message of a conversation that does not exist yet. Throws on any failure: the send that
     * carried it fails with it, and everything returns to the composer.
     */
    suspend fun upload(session: String?, filename: String?, mime: String, sizeBytes: Long, uri: String): UploadedAttachment

    /** No uploader: a deposit is a failure. Tests, and a platform without one. */
    object None : AttachmentUploader {
        override suspend fun upload(
            session: String?,
            filename: String?,
            mime: String,
            sizeBytes: Long,
            uri: String,
        ): UploadedAttachment = throw IllegalStateException("no attachment uploader")
    }
}

/** A deposited file: its path on the server, the one the message names and the tools read. */
class UploadedAttachment(val path: String)
