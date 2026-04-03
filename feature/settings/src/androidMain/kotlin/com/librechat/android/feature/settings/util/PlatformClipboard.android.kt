package com.librechat.android.feature.settings.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.mp.KoinPlatformTools

actual fun copyToClipboard(text: String, label: String) {
    val context = KoinPlatformTools.defaultContext().get().get<Context>()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
