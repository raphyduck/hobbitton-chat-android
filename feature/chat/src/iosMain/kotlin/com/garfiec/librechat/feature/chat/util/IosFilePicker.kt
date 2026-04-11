package com.garfiec.librechat.feature.chat.util

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.lastPathComponent
import platform.Foundation.timeIntervalSince1970
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeContent
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/**
 * Strong reference to the currently active picker delegate.
 * Prevents Kotlin/Native GC from collecting the delegate while the picker is open.
 * Only one picker can be open at a time, so a single reference suffices.
 */
private var activePickerDelegate: NSObject? = null

private fun getRootViewController(): UIViewController? {
    val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull() as? UIWindowScene
    return scene?.keyWindow?.rootViewController?.let { root ->
        var top = root
        while (top.presentedViewController != null) {
            top = top.presentedViewController!!
        }
        top
    }
}

/**
 * Opens UIDocumentPickerViewController for general file selection.
 * Calls [onResult] with a list of [IosFileData] or [IosImageData].
 */
fun openDocumentPicker(onResult: (List<Any>) -> Unit) {
    dispatch_async(dispatch_get_main_queue()) {
        val viewController = getRootViewController() ?: run {
            Logger.w { "IosFilePicker: no root view controller" }
            return@dispatch_async
        }

        val types = listOf(UTTypeContent ?: UTType.typeWithIdentifier("public.content")!!)
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types)
        picker.allowsMultipleSelection = true

        val delegate = DocumentPickerDelegate(onResult)
        picker.delegate = delegate
        activePickerDelegate = delegate

        viewController.presentViewController(picker, animated = true, completion = null)
    }
}

/**
 * Opens PHPickerViewController for photo/image selection.
 */
fun openPhotoPicker(onResult: (List<Any>) -> Unit) {
    dispatch_async(dispatch_get_main_queue()) {
        val viewController = getRootViewController() ?: run {
            Logger.w { "IosFilePicker: no root view controller" }
            return@dispatch_async
        }

        val config = PHPickerConfiguration()
        config.selectionLimit = 10
        config.filter = PHPickerFilter.imagesFilter

        val picker = PHPickerViewController(configuration = config)
        val delegate = PhotoPickerDelegate(onResult)
        picker.delegate = delegate
        activePickerDelegate = delegate

        viewController.presentViewController(picker, animated = true, completion = null)
    }
}

/**
 * Opens UIImagePickerController for camera capture.
 * On simulator (no camera hardware), calls onResult with empty list.
 */
fun openCamera(onResult: (List<Any>) -> Unit) {
    dispatch_async(dispatch_get_main_queue()) {
        val viewController = getRootViewController() ?: run {
            Logger.w { "IosFilePicker: no root view controller" }
            return@dispatch_async
        }

        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            Logger.w { "IosFilePicker: camera not available (simulator?)" }
            onResult(emptyList())
            return@dispatch_async
        }

        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.allowsEditing = false

        val delegate = CameraDelegate(onResult)
        picker.delegate = delegate
        activePickerDelegate = delegate

        viewController.presentViewController(picker, animated = true, completion = null)
    }
}

// ── Delegates ──────────────────────────────────────────────────

private class DocumentPickerDelegate(
    private val onResult: (List<Any>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    @OptIn(ExperimentalForeignApi::class)
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val results = mutableListOf<Any>()
        for (item in didPickDocumentsAtURLs) {
            val url = item as? NSURL ?: continue
            val accessing = url.startAccessingSecurityScopedResource()
            try {
                val data = NSData.dataWithContentsOfURL(url) ?: continue
                val bytes = data.toByteArray() ?: continue
                val filename = url.lastPathComponent ?: "file"
                val mimeType = guessMimeType(filename)

                if (mimeType.startsWith("image/")) {
                    val image = UIImage(data = data)
                    val cgImage = image?.CGImage
                    results.add(
                        IosImageData(
                            bytes = bytes,
                            filename = filename,
                            mimeType = mimeType,
                            width = cgImage?.let { CGImageGetWidth(it).toInt() },
                            height = cgImage?.let { CGImageGetHeight(it).toInt() },
                        ),
                    )
                } else {
                    results.add(IosFileData(bytes = bytes, filename = filename, mimeType = mimeType))
                }
            } finally {
                if (accessing) url.stopAccessingSecurityScopedResource()
            }
        }
        activePickerDelegate = null
        onResult(results)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        activePickerDelegate = null
    }
}

private class PhotoPickerDelegate(
    private val onResult: (List<Any>) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    @OptIn(ExperimentalForeignApi::class)
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)

        if (didFinishPicking.isEmpty()) {
            activePickerDelegate = null
            onResult(emptyList())
            return
        }

        val results = mutableListOf<Any>()
        var remaining = didFinishPicking.size

        for (item in didFinishPicking) {
            val pickerResult = item as? PHPickerResult
            if (pickerResult == null) {
                remaining--
                if (remaining == 0) onResult(results)
                continue
            }
            val provider = pickerResult.itemProvider
            if (provider.hasItemConformingToTypeIdentifier("public.image")) {
                provider.loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
                    val imageData = data?.let { nsData ->
                        val bytes = nsData.toByteArray()
                        if (bytes != null) {
                            val filename = provider.suggestedName ?: "photo"
                            val image = UIImage(data = nsData)
                            val cgImage = image?.CGImage
                            val ext = if (filename.contains(".")) "" else ".jpg"
                            IosImageData(
                                bytes = bytes,
                                filename = "$filename$ext",
                                mimeType = "image/jpeg",
                                width = cgImage?.let { CGImageGetWidth(it).toInt() },
                                height = cgImage?.let { CGImageGetHeight(it).toInt() },
                            )
                        } else {
                            null
                        }
                    }
                    if (imageData == null) {
                        Logger.e { "PHPicker: failed to load image: ${error?.localizedDescription}" }
                    }
                    dispatch_async(dispatch_get_main_queue()) {
                        if (imageData != null) results.add(imageData)
                        remaining--
                        if (remaining == 0) {
                            activePickerDelegate = null
                            onResult(results)
                        }
                    }
                }
            } else {
                remaining--
                if (remaining == 0) onResult(results)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class CameraDelegate(
    private val onResult: (List<Any>) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        activePickerDelegate = null

        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        if (image != null) {
            val data = UIImageJPEGRepresentation(image, 0.85) ?: return
            val bytes = data.toByteArray() ?: return
            val cgImage = image.CGImage
            val width = cgImage?.let { CGImageGetWidth(it).toInt() }
            val height = cgImage?.let { CGImageGetHeight(it).toInt() }
            val timestamp = NSDate().timeIntervalSince1970.toLong()
            onResult(
                listOf(
                    IosImageData(
                        bytes = bytes,
                        filename = "photo_$timestamp.jpg",
                        mimeType = "image/jpeg",
                        width = width,
                        height = height,
                    ),
                ),
            )
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        activePickerDelegate = null
    }
}

// ── Utilities ──────────────────────────────────────────────────

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray? {
    val length = this.length.toInt()
    if (length == 0) return null
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
    }
    return bytes
}

private fun guessMimeType(filename: String): String {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "zip" -> "application/zip"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        else -> "application/octet-stream"
    }
}
