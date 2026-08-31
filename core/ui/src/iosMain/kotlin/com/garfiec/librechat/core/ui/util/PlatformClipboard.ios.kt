package com.garfiec.librechat.core.ui.util

import platform.UIKit.UIPasteboard

actual fun copyToClipboard(text: String, label: String) {
    UIPasteboard.generalPasteboard.string = text
}
