package com.garfiec.librechat.core.ui.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import org.koin.core.context.GlobalContext

actual fun copyToClipboard(text: String, label: String) {
    val context = GlobalContext.get().get<Context>()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    // What a message holds may be an IBAN, a code, a mail (26/10/2026): Android 13 then keeps it
    // out of its clipboard preview and tells keyboards and clipboard managers to leave it alone.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    clipboard.setPrimaryClip(clip)
}
