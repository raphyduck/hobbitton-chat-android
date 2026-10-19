package com.garfiec.librechat.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a reboot or an update, the push service comes back by itself (19/10/2026). */
class PushBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            PushService.start(context)
        }
    }
}
