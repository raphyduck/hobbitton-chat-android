package com.garfiec.librechat.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import com.garfiec.librechat.attention.AndroidAttentionNotifier
import com.garfiec.librechat.core.common.di.AppThreads
import com.garfiec.librechat.core.common.network.CleartextPolicy
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.engine.AttentionNotifier
import com.garfiec.librechat.core.data.engine.attentionWatcherRunning
import com.garfiec.librechat.core.data.engine.parsePushLine
import com.garfiec.librechat.core.data.engine.shouldNotifyPush
import com.garfiec.librechat.core.data.portal.PortalSession
import com.garfiec.librechat.core.data.scheduler.SchedulerRepository
import com.garfiec.librechat.core.model.scheduler.PushChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The server's push notifications, followed by the app itself (19/10/2026, Raphaël's rule: no
 * third-party app, nothing to configure). The scheduler says where it publishes
 * (`GET /notifications`, behind the portal); this service keeps ntfy's JSON stream of that topic
 * open and turns each message into the app's own notification: a decision waiting in a
 * conversation, or a question an agent asked while the app was closed.
 *
 * A foreground service, as Android requires to keep a connection while the app is in the
 * background; its own notification is as discreet as Android allows (Raphaël, 19/10/2026: keep
 * the stream, make the notice discreet): the lowest channel, no badge, no sound, no time, hidden on
 * the lock screen, dismissable, and never posted again once the service runs, so a notice swiped
 * away stays away until the phone restarts. It stops by itself when the Settings
 * switch is off or the platform has no push channel, and resumes the stream after the last
 * message it showed, so a reconnection loses nothing and replays nothing.
 */
class PushService : Service() {

    private val scheduler: SchedulerRepository by inject()
    private val settings: SettingsDataStore by inject()
    private val notifier: AttentionNotifier by inject()
    private val connectivity: ConnectivityObserver by inject()
    private val portal: PortalSession by inject()

    private val scope = CoroutineScope(SupervisorJob() + AppThreads.io)
    private var listening: Job? = null

    /** The stream being read, to cut it from another thread: the read itself cannot be interrupted. */
    @Volatile
    private var current: HttpURLConnection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Once: a sticky restart with no intent would otherwise post the notice again after the
        // person swiped it away (26/10/2026).
        if (listening?.isActive != true) {
            goForeground()
            listening = scope.launch { listen() }
            // The Settings switch turned off cuts the stream at once, where the loop used to see
            // it at the next drop only, hours later on a quiet topic.
            scope.launch { settings.attentionSound.collect { on -> if (!on) current?.disconnect() } }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        current?.disconnect()
        super.onDestroy()
    }

    private suspend fun listen() {
        var wait = FIRST_RETRY_MS
        while (scope.isActive) {
            if (!settings.attentionSound.first()) {
                stopSelf()
                return
            }
            // No blind retries in a tunnel or in airplane mode (26/10/2026): wait for a network.
            connectivity.isConnected.first { it }
            val channel = scheduler.pushChannel()
            if (channel == null) {
                if (!portal.hasTokens()) {
                    // Signed out: nothing to follow until the next sign-in starts the service again.
                    stopSelf()
                    return
                }
                // No scheduler, no route, or no ntfy: look again later.
                delay(NO_CHANNEL_RETRY_MS)
                continue
            }
            // Without the throwable (26/10/2026): HttpURLConnection names the URL in its message,
            // topic included, and the journal keeps a URL's path.
            val received = runCatching { follow(channel) }
                .onFailure { Logger.w { "Push: the stream dropped (${it::class.simpleName}), reconnecting" } }
                .getOrDefault(false)
            wait = if (received) FIRST_RETRY_MS else (wait * 2).coerceAtMost(MAX_RETRY_MS)
            delay(wait)
        }
    }

    /** One connection to the topic's stream, until it drops. True if anything came through. */
    private fun follow(channel: PushChannel): Boolean {
        val address = channel.streamUrl(since())
        // Off the Ktor clients and their cleartext guard: the same rule by hand (26/10/2026), and
        // no redirection followed, which could carry the topic to another host.
        if (!CleartextPolicy.isPermitted(address)) {
            Logger.w { "Push: the channel is cleartext to a public host, not followed" }
            return false
        }
        val connection = URL(address).openConnection() as HttpURLConnection
        current = connection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        // ntfy sends a keepalive every 45 s: a longer silence is a dead connection.
        connection.readTimeout = READ_TIMEOUT_MS
        var received = false
        try {
            connection.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    received = true
                    val message = parsePushLine(line) ?: continue
                    remember(message.id)
                    if (shouldNotifyPush(message.kind, notifier.inForeground, attentionWatcherRunning)) {
                        (notifier as? AndroidAttentionNotifier)?.showPush(message)
                    }
                }
            }
        } finally {
            current = null
            connection.disconnect()
        }
        return received
    }

    private fun since(): String =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_ID, null) ?: FIRST_SINCE

    private fun remember(id: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST_ID, id).apply()
    }

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // A channel's settings are fixed once created: the first one (badge, lock screen) goes.
            manager?.deleteNotificationChannel(OLD_CHANNEL_LISTENING)
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_LISTENING,
                    getString(R.string.push_channel_listening),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = getString(R.string.push_channel_listening_hint)
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                },
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_LISTENING)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(getString(R.string.push_listening))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setShowWhen(false)
            .setSilent(true)
            .setLocalOnly(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        ServiceCompat.startForeground(this, LISTENING_ID, notification, type)
    }

    companion object {
        private const val PREFS = "push"
        private const val KEY_LAST_ID = "last_id"

        /** A first stream reads back what the last hour published, so nothing just sent is missed. */
        private const val FIRST_SINCE = "1h"
        private const val CHANNEL_LISTENING = "butler_listening_discreet"
        private const val OLD_CHANNEL_LISTENING = "butler_listening"
        private const val LISTENING_ID = 4242

        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 120_000
        private const val FIRST_RETRY_MS = 5_000L
        private const val MAX_RETRY_MS = 300_000L
        private const val NO_CHANNEL_RETRY_MS = 120_000L

        /** True between the service's creation and its end, in this process. */
        @Volatile
        private var running = false

        /**
         * Starts the service unless it runs already. Called at every sign-in and when the Settings
         * switch turns on (26/10/2026; before, at every launch, signed in or not): a running service
         * is not asked again, which would post its notice again after it was swiped away.
         */
        fun start(context: Context) {
            if (running) return
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, PushService::class.java))
            }.onFailure { Logger.w(it) { "Push: the service could not start" } }
        }
    }
}
