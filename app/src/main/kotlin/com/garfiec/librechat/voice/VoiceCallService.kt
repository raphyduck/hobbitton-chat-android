package com.garfiec.librechat.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.garfiec.librechat.R
import com.garfiec.librechat.shared.engine.voice.VoiceLink
import org.koin.android.ext.android.inject

/**
 * The voice mode in the background (28/10/2026, Raphaël: « implémente le mode arrière-plan pour le
 * mode vocal »). Android silences the microphone of an app that leaves the foreground unless a
 * foreground service of type `microphone` runs; this one runs while the room is joined, nothing
 * else: [LiveKitVoiceLink] starts it as it joins (the screen is in front, as Android 14 requires
 * for this type) and stops it as it leaves. Its notification says the agent listens, opens the app
 * on a tap and hangs up from its action. Swiping the app away from the recent apps hangs up too:
 * a call with no screen left to show it would go on unseen.
 */
class VoiceCallService : Service() {

    private val link: VoiceLink by inject()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // At once: a stop that arrives before onStartCommand must find the service in front.
        goForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HANG_UP) {
            Logger.i { "Voice: hung up from the notification" }
            link.disconnect()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Logger.i { "Voice: app removed from the recent apps, hanging up" }
        link.disconnect()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun goForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_VOICE,
                    getString(R.string.voice_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.voice_channel_hint)
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val hangUp = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceCallService::class.java).setAction(ACTION_HANG_UP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_VOICE)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.voice_ongoing))
            .setContentText(getString(R.string.voice_ongoing_hint))
            .setContentIntent(open)
            .addAction(0, getString(R.string.voice_hang_up), hangUp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setLocalOnly(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        runCatching { ServiceCompat.startForeground(this, VOICE_ID, notification, type) }
            .onFailure {
                // Refused (the microphone permission withdrawn, the app no longer in front): the
                // room goes on while the screen is shown, as before this service existed.
                Logger.w(it) { "Voice: the foreground service was refused" }
                stopSelf()
            }
    }

    companion object {
        private const val CHANNEL_VOICE = "eva_voice"
        private const val VOICE_ID = 4343
        private const val ACTION_HANG_UP = "at.hobbitton.chat.voice.HANG_UP"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, VoiceCallService::class.java)) }
                .onFailure { Logger.w(it) { "Voice: the foreground service could not start" } }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceCallService::class.java))
        }
    }
}
