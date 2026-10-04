package com.garfiec.librechat.attention

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.garfiec.librechat.MainActivity
import com.garfiec.librechat.R
import com.garfiec.librechat.core.data.engine.AttentionNotifier
import com.garfiec.librechat.core.data.engine.EngineSessionKind
import com.garfiec.librechat.core.data.engine.OpenConversation

/**
 * Android's half of [AttentionNotifier]: the notification sound, and two notification channels
 * (one for questions, one for finished replies), so the person can tune each in the system settings
 * (a question blocks its turn; a finished reply only waits to be read).
 *
 * Both channels are high importance, which is what makes Android sound and peek the notification;
 * their sound is the device's default notification sound. When the app is on screen and the
 * conversation is the one in front of the person, [AttentionSignals] asks for [chime] instead: the
 * same sound, no banner over the form they are about to fill.
 *
 * Without `POST_NOTIFICATIONS` (Android 13+, refused or not asked yet), posting is skipped in
 * silence: the activity asks for it, and the Settings switch asks again.
 */
class AndroidAttentionNotifier(private val context: Context) : AttentionNotifier {

    private val manager = NotificationManagerCompat.from(context)

    init {
        createChannels()
    }

    override val inForeground: Boolean
        get() {
            // Asked of the system rather than tracked: this object is built lazily, possibly after
            // the activity started, and a count of started activities would then begin wrong.
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }

    override fun chime() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            RingtoneManager.getRingtone(context, uri)?.apply {
                // Notification usage: silenced by the ringer switch and Do Not Disturb, like any
                // notification sound, never by the media volume.
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        }.onFailure { Logger.w(it) { "Attention: the notification sound could not be played" } }
    }

    override fun showQuestion(conversation: OpenConversation, requestId: String, question: String) {
        post(
            id = questionId(requestId),
            channel = CHANNEL_QUESTIONS,
            title = conversation.title?.let { context.getString(R.string.attention_question_in, it) }
                ?: context.getString(R.string.attention_question),
            text = question.ifBlank { context.getString(R.string.attention_question_hint) },
            conversation = conversation,
        )
    }

    override fun showReplyReady(conversation: OpenConversation) {
        post(
            id = replyId(conversation.sessionId),
            channel = CHANNEL_REPLIES,
            title = context.getString(R.string.attention_reply_ready),
            text = conversation.title ?: context.getString(R.string.attention_reply_ready_hint),
            conversation = conversation,
        )
    }

    override fun cancelQuestion(requestId: String) = manager.cancel(questionId(requestId))

    override fun cancelReplyReady(sessionId: String) = manager.cancel(replyId(sessionId))

    private fun post(id: Int, channel: String, title: String, text: String, conversation: OpenConversation) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent(id, conversation))
            .build()
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // Revoked between the check and the post.
            Logger.w(e) { "Attention: notification permission withdrawn" }
        }
    }

    private fun canPost(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Back to the app, on the conversation: `MainActivity` hands the extras to the shell. */
    private fun openIntent(id: Int, conversation: OpenConversation): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_SESSION_ID, conversation.sessionId)
            .putExtra(EXTRA_TITLE, conversation.title)
            .putExtra(EXTRA_KIND, conversation.kind?.name)
        return PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_QUESTIONS,
                context.getString(R.string.attention_channel_questions),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.attention_channel_questions_hint) },
        )
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REPLIES,
                context.getString(R.string.attention_channel_replies),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.attention_channel_replies_hint) },
        )
    }

    // One notification per question, one per conversation's reply: a second reply replaces the
    // first rather than stacking « ready » twice for the same chat.
    private fun questionId(requestId: String) = "q:$requestId".hashCode()

    private fun replyId(sessionId: String) = "r:$sessionId".hashCode()

    companion object {
        const val EXTRA_SESSION_ID = "at.hobbitton.chat.extra.SESSION_ID"
        const val EXTRA_TITLE = "at.hobbitton.chat.extra.TITLE"
        const val EXTRA_KIND = "at.hobbitton.chat.extra.KIND"

        private const val CHANNEL_QUESTIONS = "butler_questions"
        private const val CHANNEL_REPLIES = "butler_replies"

        /** The conversation a notification's intent names, or null for any other intent. */
        fun conversationOf(intent: Intent?): OpenConversation? {
            val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() } ?: return null
            return OpenConversation(
                sessionId = sessionId,
                title = intent.getStringExtra(EXTRA_TITLE),
                kind = intent.getStringExtra(EXTRA_KIND)?.let { name ->
                    EngineSessionKind.entries.firstOrNull { it.name == name }
                },
            )
        }
    }
}
