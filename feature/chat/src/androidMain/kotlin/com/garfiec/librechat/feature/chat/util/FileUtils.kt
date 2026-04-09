package com.garfiec.librechat.feature.chat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import co.touchlab.kermit.Logger
import java.io.ByteArrayOutputStream

/**
 * Resolves a human-readable filename from a content URI using the ContentResolver.
 * Falls back to the last path segment if DISPLAY_NAME is not available.
 */
internal fun resolveFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
    }
    return uri.lastPathSegment
}

/**
 * Guesses MIME type from file extension. Defaults to application/octet-stream.
 */
internal fun guessMimeType(filename: String): String {
    val extension = filename.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: "application/octet-stream"
}

/**
 * Detects the actual MIME type of image data by inspecting magic bytes (file signature).
 *
 * This is critical because Android's ContentResolver.getType() and file extension-based
 * guessing can return incorrect MIME types. For example, a PNG screenshot might have a
 * .jpg extension, or a WebP image shared from another app may be reported as image/jpeg.
 * When the declared MIME type doesn't match the actual data, AI providers (e.g. Anthropic)
 * reject the image with a 400 error.
 *
 * Returns the detected MIME type for known image formats, or null if the bytes don't
 * match any recognized image signature.
 *
 * Supported formats: JPEG, PNG, GIF, WebP, BMP, TIFF, HEIF/HEIC, AVIF, ICO, SVG.
 */
internal fun detectMimeTypeFromBytes(bytes: ByteArray): String? {
    if (bytes.size < 12) return null

    // JPEG: FF D8 FF
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
        return "image/jpeg"
    }

    // PNG: 89 50 4E 47 0D 0A 1A 0A
    if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
        bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
        bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()
    ) {
        return "image/png"
    }

    // GIF: "GIF87a" or "GIF89a"
    if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
        bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte() &&
        (bytes[4] == 0x37.toByte() || bytes[4] == 0x39.toByte()) &&
        bytes[5] == 0x61.toByte()
    ) {
        return "image/gif"
    }

    // WebP: "RIFF" at offset 0 and "WEBP" at offset 8
    if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
        bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
        bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
        bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
    ) {
        return "image/webp"
    }

    // BMP: "BM"
    if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) {
        return "image/bmp"
    }

    // TIFF: "II" (little-endian) or "MM" (big-endian) followed by 42
    if ((bytes[0] == 0x49.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x2A.toByte() && bytes[3] == 0x00.toByte()) ||
        (bytes[0] == 0x4D.toByte() && bytes[1] == 0x4D.toByte() &&
            bytes[2] == 0x00.toByte() && bytes[3] == 0x2A.toByte())
    ) {
        return "image/tiff"
    }

    // HEIF/HEIC and AVIF: ftyp box at offset 4
    if (bytes.size >= 12 &&
        bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() &&
        bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()
    ) {
        // Read the brand (4 bytes at offset 8)
        val brand = String(bytes, 8, 4, Charsets.US_ASCII)
        return when {
            brand.startsWith("heic") || brand.startsWith("heix") ||
                brand.startsWith("heim") || brand.startsWith("heis") ||
                brand.startsWith("mif1") -> "image/heic"
            brand.startsWith("avif") || brand.startsWith("avis") -> "image/avif"
            else -> null // Unknown ftyp brand
        }
    }

    // ICO: 00 00 01 00
    if (bytes[0] == 0x00.toByte() && bytes[1] == 0x00.toByte() &&
        bytes[2] == 0x01.toByte() && bytes[3] == 0x00.toByte()
    ) {
        return "image/x-icon"
    }

    return null
}

/**
 * Returns the canonical file extension for a given MIME type.
 * Used to ensure the filename extension matches the actual content type.
 */
internal fun extensionForMimeType(mimeType: String): String? = when (mimeType) {
    "image/jpeg" -> "jpg"
    "image/png" -> "png"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "image/bmp" -> "bmp"
    "image/tiff" -> "tiff"
    "image/heic" -> "heic"
    "image/heif" -> "heif"
    "image/avif" -> "avif"
    "image/x-icon" -> "ico"
    "image/svg+xml" -> "svg"
    else -> null
}

/**
 * Fixes the filename extension to match the detected MIME type.
 *
 * The LibreChat server uses the filename extension (via multer) to determine
 * whether image conversion is needed. If the extension doesn't match the
 * server's configured imageOutputType, the image gets converted but the
 * stored MIME type (from our Content-Type header) does not get updated,
 * causing a mismatch that AI providers reject.
 *
 * By ensuring the extension matches our detected MIME type, we maximize the
 * chance that extension == imageOutputType, avoiding unnecessary conversion.
 */
internal fun fixFilenameExtension(filename: String, mimeType: String): String {
    val expectedExtension = extensionForMimeType(mimeType) ?: return filename

    val dotIndex = filename.lastIndexOf('.')
    val currentExtension = if (dotIndex >= 0) {
        filename.substring(dotIndex + 1).lowercase()
    } else {
        ""
    }

    // Check if current extension already matches (accounting for jpeg/jpg equivalence)
    val normalizedCurrent = when (currentExtension) {
        "jpeg" -> "jpg"
        "tif" -> "tiff"
        else -> currentExtension
    }
    val normalizedExpected = when (expectedExtension) {
        "jpeg" -> "jpg"
        "tif" -> "tiff"
        else -> expectedExtension
    }

    if (normalizedCurrent == normalizedExpected) {
        return filename
    }

    // Replace the extension
    return if (dotIndex >= 0) {
        "${filename.substring(0, dotIndex)}.$expectedExtension"
    } else {
        "$filename.$expectedExtension"
    }
}

/**
 * Data class holding the result of image re-encoding.
 */
data class ReEncodedImage(
    val bytes: ByteArray,
    val mimeType: String,
)

/**
 * Re-encodes ALL images to PNG to prevent MIME type mismatches.
 *
 * The LibreChat server has a bug in processAgentFileUpload: it converts
 * uploaded images to its configured imageOutputType (default: PNG) using
 * sharp, but stores the *original* MIME type from the upload Content-Type
 * header. When the AI provider (e.g. Anthropic) later receives the base64
 * image, it gets converted bytes (e.g. PNG) with the original media_type
 * (e.g. image/jpeg), causing a 400 "Image does not match the provided
 * media type" error.
 *
 * The only reliable client-side fix is to ensure NO conversion happens
 * on the server. The server skips conversion when the file extension
 * matches its imageOutputType. By always re-encoding to PNG (the server
 * default) and setting the extension to .png, the server sees
 * .png == .png and leaves the bytes untouched, so the stored MIME type
 * (image/png) matches the actual bytes.
 *
 * This means JPEG photos get re-encoded to PNG, which increases file
 * size, but the alternative is a 400 error that makes the app unusable.
 * The server will resize large images anyway, so the size impact on the
 * AI provider request is bounded.
 *
 * Returns null only if the image cannot be decoded by BitmapFactory.
 */
internal fun reEncodeImageIfNeeded(bytes: ByteArray, mimeType: String): ReEncodedImage? {
    // Always re-encode to PNG to match the server's default imageOutputType.
    // Previously we skipped JPEG/PNG/GIF/WebP as "safe", but the server
    // converts non-matching extensions (e.g. .jpg when imageOutputType=png)
    // while storing the original MIME type, causing the Anthropic 400 error.
    return try {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return null // BitmapFactory can't decode this format

        // If already PNG with correct magic bytes, skip re-encoding to save CPU/memory
        if (mimeType == "image/png" && detectMimeTypeFromBytes(bytes) == "image/png") {
            bitmap.recycle()
            return null
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        bitmap.recycle()

        val encodedBytes = outputStream.toByteArray()
        // Verify the re-encoded bytes are valid PNG
        val verifiedType = detectMimeTypeFromBytes(encodedBytes)
        if (verifiedType != "image/png") {
            Logger.w { "reEncodeImageIfNeeded: re-encoded bytes don't match expected type image/png (got $verifiedType), using original" }
            return null
        }

        Logger.d { "reEncodeImageIfNeeded: re-encoded $mimeType -> image/png (${bytes.size} -> ${encodedBytes.size} bytes)" }
        ReEncodedImage(bytes = encodedBytes, mimeType = "image/png")
    } catch (e: Exception) {
        Logger.w(e) { "reEncodeImageIfNeeded: failed to re-encode $mimeType, using original bytes" }
        null
    }
}
