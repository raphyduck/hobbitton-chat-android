package com.librechat.android.feature.chat.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIPasteboard
import platform.posix.memcpy

/**
 * Checks whether the system clipboard currently contains an image.
 */
fun clipboardHasImage(): Boolean {
    return UIPasteboard.generalPasteboard.hasImages
}

/**
 * Reads the first image from the system clipboard and returns it as [IosImageData],
 * or null if no image is available.
 *
 * The image is converted to PNG format for consistent server-side handling.
 */
@OptIn(ExperimentalForeignApi::class)
fun readClipboardImage(): IosImageData? {
    val image: UIImage = UIPasteboard.generalPasteboard.image ?: return null

    val pngData: NSData = UIImagePNGRepresentation(image) ?: return null
    val bytes = pngData.toByteArray() ?: return null
    if (bytes.isEmpty()) return null

    val cgImage = image.CGImage
    val width = if (cgImage != null) CGImageGetWidth(cgImage).toInt() else null
    val height = if (cgImage != null) CGImageGetHeight(cgImage).toInt() else null

    val timestamp = NSDate().timeIntervalSince1970.toLong()

    return IosImageData(
        bytes = bytes,
        filename = "clipboard_${timestamp}.png",
        mimeType = "image/png",
        width = width,
        height = height,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray? {
    val length = this.length.toInt()
    if (length == 0) return null
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}
