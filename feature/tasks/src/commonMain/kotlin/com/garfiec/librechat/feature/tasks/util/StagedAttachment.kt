package com.garfiec.librechat.feature.tasks.util

import com.garfiec.librechat.core.model.engine.EnginePromptPart
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A photo staged in the composer, ready to travel with the next message.
 *
 * The bytes are already **downscaled** by the picker's platform side: the engine has no upload
 * route, a file rides inside the message as a base64 data URL, and a 12 MB camera photo would put
 * a 16 MB string into one HTTP body — and into the model's context. The vision ceiling is ~1.5 k
 * pixels a side anyway; past that the provider shrinks it itself and the extra bytes buy nothing.
 *
 * Not a data class: a `ByteArray` field breaks `equals`/`hashCode` structural promises (detekt's
 * ArrayInDataClass), and identity by [id] is what the UI actually needs to remove one chip.
 */
class StagedAttachment(
    val id: String,
    val mime: String,
    val filename: String?,
    val bytes: ByteArray,
    /**
     * Set when the file does not ride in the message but is deposited on the server first
     * (06/11/2026: a video, or any file past what a message carries): the platform URI the
     * uploader streams from. [bytes] then hold a preview (a video's first frame as JPEG), or nothing.
     */
    val uploadUri: String? = null,
    /** The file's size, what the deposit announces and the chip shows. */
    val sizeBytes: Long = bytes.size.toLong(),
) {
    /** Deposited on the server at send time rather than carried in the message. */
    val deposited: Boolean get() = uploadUri != null

    /** The model will see a picture: the photo itself, or a deposited video's first frame. */
    val showsAsImage: Boolean get() = if (deposited) bytes.isNotEmpty() else mime.startsWith("image/")

    override fun equals(other: Any?): Boolean = other is StagedAttachment && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

/**
 * A deposited video's first frame as the engine wants it, so the model sees the scene the words
 * describe; null when there is no preview.
 */
@OptIn(ExperimentalEncodingApi::class)
fun StagedAttachment.previewPart(): EnginePromptPart? {
    if (!deposited || bytes.isEmpty()) return null
    return EnginePromptPart.file(
        mime = "image/jpeg",
        dataUrl = "data:image/jpeg;base64," + Base64.encode(bytes),
        filename = (filename ?: "video").substringBeforeLast('.') + " (image).jpg",
    )
}

/** The staged photo as the engine wants it: a `type = "file"` part carrying a data URL. */
@OptIn(ExperimentalEncodingApi::class)
fun StagedAttachment.asPromptPart(): EnginePromptPart =
    EnginePromptPart.file(
        mime = mime,
        dataUrl = "data:$mime;base64," + Base64.encode(bytes),
        filename = filename,
    )
