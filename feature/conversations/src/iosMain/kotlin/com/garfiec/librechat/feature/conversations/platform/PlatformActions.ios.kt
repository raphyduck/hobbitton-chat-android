package com.garfiec.librechat.feature.conversations.platform

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import platform.UIKit.UIPasteboard

actual fun copyToClipboard(text: String, label: String) {
    UIPasteboard.generalPasteboard.string = text
}

actual fun showToast(message: String) {
    // iOS doesn't have native Toast — show a brief alert-style overlay.
    Logger.i("Toast") { message }
    // Use UIAlertController as a lightweight toast replacement
    val scene = platform.UIKit.UIApplication.sharedApplication.connectedScenes
        .firstOrNull() as? platform.UIKit.UIWindowScene
    val rootVc = scene?.windows?.firstOrNull {
        (it as? platform.UIKit.UIWindow)?.isKeyWindow() == true
    }?.let { (it as platform.UIKit.UIWindow).rootViewController } ?: return
    val alert = platform.UIKit.UIAlertController.alertControllerWithTitle(
        title = null,
        message = message,
        preferredStyle = platform.UIKit.UIAlertControllerStyleAlert,
    )
    rootVc.presentViewController(alert, animated = true, completion = null)
    // Auto-dismiss after 1.5 seconds
    platform.Foundation.NSTimer.scheduledTimerWithTimeInterval(
        interval = 1.5,
        repeats = false,
    ) {
        alert.dismissViewControllerAnimated(true, completion = null)
    }
}

@Composable
actual fun FileSaver(
    triggerFileName: String?,
    content: String?,
    onComplete: (success: Boolean, message: String?) -> Unit,
    onReset: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    if (triggerFileName != null && content != null && !showDialog) {
        showDialog = true
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                onComplete(false, null)
                onReset()
            },
            title = {
                Text(
                    text = "Export Not Available",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = "File export is not yet available on iOS. This feature will be added in a future update.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        onComplete(false, "File export not yet available on iOS")
                        onReset()
                    },
                ) {
                    Text("OK")
                }
            },
        )
    }
}
