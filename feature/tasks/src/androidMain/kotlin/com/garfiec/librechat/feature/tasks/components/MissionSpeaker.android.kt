package com.garfiec.librechat.feature.tasks.components

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
internal actual fun rememberMissionSpeaker(onDone: () -> Unit): MissionSpeaker? {
    val context = LocalContext.current
    val doneNow by rememberUpdatedState(onDone)
    var speaking by remember { mutableStateOf(false) }
    val voice = remember {
        PhoneVoice(context) {
            speaking = false
            doneNow()
        }
    }
    DisposableEffect(voice) { onDispose { voice.release() } }
    return MissionSpeaker(
        speaking = speaking,
        speak = {
            speaking = true
            voice.speak(it)
        },
        stop = {
            voice.stop()
            speaking = false
        },
    )
}

/**
 * Android's [TextToSpeech], the engine and voice the phone is set up with, in the device's
 * language. The engine binds asynchronously: a text given before it is ready waits for it. A long
 * text is spoken in pieces under the engine's own limit, and [onDone] fires once, after the last.
 * The engine's callbacks come on its own thread; everything here runs on the main one.
 */
private class PhoneVoice(context: Context, private val onDone: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var ready: Boolean? = null
    private var pending: String? = null
    private var lastUtterance: String? = null
    private var count = 0

    private val engine: TextToSpeech = TextToSpeech(context) { status ->
        main.post {
            ready = status == TextToSpeech.SUCCESS
            if (ready == true) {
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                engine.language = Locale.getDefault()
                engine.setOnUtteranceProgressListener(progress)
            }
            pending?.let { speak(it) }
            pending = null
        }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = ended(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = ended(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = ended(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = ended(utteranceId)

        private fun ended(utteranceId: String?) {
            main.post { if (utteranceId != null && utteranceId == lastUtterance) finish() }
        }
    }

    fun speak(text: String) {
        when (ready) {
            null -> pending = text
            false -> finish()
            true -> {
                val pieces = pieces(text, TextToSpeech.getMaxSpeechInputLength())
                if (pieces.isEmpty()) return finish()
                pieces.forEachIndexed { index, piece ->
                    val id = "eva-${++count}"
                    lastUtterance = id
                    val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    if (engine.speak(piece, mode, Bundle(), id) != TextToSpeech.SUCCESS) return finish()
                }
            }
        }
    }

    fun stop() {
        pending = null
        lastUtterance = null
        if (ready == true) engine.stop()
    }

    fun release() {
        stop()
        engine.shutdown()
    }

    private fun finish() {
        lastUtterance = null
        onDone()
    }

    /** Cut at paragraph, then sentence ends, so no piece exceeds what the engine takes in one go. */
    private fun pieces(text: String, limit: Int): List<String> {
        val out = mutableListOf<String>()
        var rest = text.trim()
        while (rest.length > limit) {
            val cut = listOf("\n", ". ", "! ", "? ", ", ", " ")
                .map { rest.lastIndexOf(it, limit - 1) }
                .firstOrNull { it > limit / 2 } ?: (limit - 1)
            out += rest.substring(0, cut + 1).trim()
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out.filter { it.isNotBlank() }
    }
}
