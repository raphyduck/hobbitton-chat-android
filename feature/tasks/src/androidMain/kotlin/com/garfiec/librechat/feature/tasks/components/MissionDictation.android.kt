package com.garfiec.librechat.feature.tasks.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.garfiec.librechat.core.data.engine.TranscriptionFailure
import com.garfiec.librechat.core.data.engine.TranscriptionOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Two ways behind one two-state button: the phone's own speech recognition ([PhoneDictation]),
 * when [onDevice] asks for it and the phone has one, else a MediaRecorder whose take goes to the
 * scheduler. The recorder's blocking calls (prepare/start/stop and the file read) run on the IO
 * dispatcher; the recognizer lives on the main thread, as it must.
 */
@Composable
internal actual fun rememberMissionDictation(
    onDevice: Boolean,
    onCapture: (PickedAudio) -> Unit,
    onHear: (String) -> Unit,
    onError: (TranscriptionOutcome.Failed) -> Unit,
): MissionDictation? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { DictationRecorder(context) }
    var recording by remember { mutableStateOf(false) }

    val heardNow by rememberUpdatedState(onHear)
    val failedNow by rememberUpdatedState(onError)
    var listening by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }
    // The phone has no French yet: the scheduler hears this screen's dictations meanwhile.
    var serverOnly by remember { mutableStateOf(false) }
    val phone = remember {
        PhoneDictation(
            context = context,
            onPartial = { heard = it },
            onHeard = { heardNow(it) },
            onEnd = { error ->
                listening = false
                heard = ""
                when {
                    error == null -> Unit
                    error in LANGUAGE_ERRORS -> {
                        serverOnly = true
                        failedNow(TranscriptionOutcome.Failed(TranscriptionFailure.DEVICE_LANGUAGE))
                    }
                    else -> failedNow(TranscriptionOutcome.Failed(TranscriptionFailure.DEVICE))
                }
            },
        )
    }

    // Leaving the screen mid-dictation throws the take away — there is nobody left to stop it for.
    DisposableEffect(recorder, phone) {
        onDispose {
            recorder.discard()
            phone.release()
        }
    }

    val begin: () -> Unit = {
        if (onDevice && !serverOnly && PhoneDictation.available(context)) {
            listening = phone.start()
        } else {
            scope.launch {
                recording = withContext(Dispatchers.IO) { recorder.begin() }
            }
        }
    }
    val permission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) begin() }

    return MissionDictation(
        recording = recording || listening,
        listening = listening,
        heard = heard,
        toggle = {
            when {
                // The last words still come, then the end: the button stays on stop until they have.
                listening -> phone.stop()
                recording -> {
                    recording = false
                    scope.launch {
                        val bytes = withContext(Dispatchers.IO) { recorder.stop() }
                        bytes?.takeIf { it.isNotEmpty() }
                            ?.let { onCapture(PickedAudio(it, recorder.mime, recorder.filename)) }
                    }
                }
                else -> {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) begin() else permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
    )
}

/**
 * The phone's own speech recognition, on the device only (19/10/2026): Android 12's on-device
 * recognizer, never the network one — that one sends the voice to Google, which is worse than
 * sending it to our own scheduler. Where the phone has none, [available] is false and the
 * dictation records for the scheduler instead.
 *
 * Android's recognizer stops by itself after a pause; the person's tap is what ends a dictation
 * here, so each stretch of speech is handed over ([onHeard]) and listening starts again, until the
 * tap ([stop]) or [MAX_SILENT_RESTARTS] stretches of silence in a row. Every method runs on the
 * main thread.
 *
 * @param onEnd the dictation is over: null for a normal end, else the recognizer's error code.
 */
private class PhoneDictation(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onHeard: (String) -> Unit,
    private val onEnd: (error: Int?) -> Unit,
) : RecognitionListener {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wanted = false
    private var silentRestarts = 0

    fun start(): Boolean = runCatching {
        wanted = true
        silentRestarts = 0
        listen()
        true
    }.getOrElse {
        wanted = false
        false
    }

    /** Asks for the end: the words being heard still arrive ([onResults]), then [onEnd]. */
    fun stop() {
        wanted = false
        recognizer?.stopListening()
    }

    fun release() {
        wanted = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {
        val current = recognizer ?: create().also {
            it.setRecognitionListener(this)
            recognizer = it
        }
        current.startListening(intent())
    }

    private fun create(): SpeechRecognizer =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            error("No on-device recognition before Android 12")
        }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, DICTATION_LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    /** Listening again on the next turn of the loop, not from inside the recognizer's own callback. */
    private fun again() {
        main.post { if (wanted) runCatching { listen() }.onFailure { end(SpeechRecognizer.ERROR_CLIENT) } }
    }

    private fun end(error: Int?) {
        wanted = false
        onEnd(error)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        onPartial(firstOf(partialResults).orEmpty())
    }

    override fun onResults(results: Bundle?) {
        val text = firstOf(results)
        if (!text.isNullOrBlank()) {
            silentRestarts = 0
            onHeard(text)
        }
        onPartial("")
        if (wanted) again() else end(null)
    }

    override fun onError(error: Int) {
        val silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        when {
            error in LANGUAGE_ERRORS -> {
                // Asks the phone to fetch its French, for the next dictation (Android 13+).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runCatching { recognizer?.triggerModelDownload(intent()) }
                }
                end(error)
            }
            // The person tapped stop: whatever the recognizer says now, the dictation simply ends.
            !wanted -> end(null)
            silence && silentRestarts < MAX_SILENT_RESTARTS -> {
                silentRestarts++
                again()
            }
            silence -> end(null)
            else -> end(error)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun firstOf(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    companion object {
        fun available(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
    }
}

/**
 * The chat's recorder choices, restated here because a feature module cannot see another's code:
 * OGG/Opus on API 29+, AAC in MP4 (m4a) before — both types the scheduler's transcription takes,
 * where the chat's older 3GP/AMR is not — 16 kHz mono, a cache file that is read once and
 * deleted. Every method is blocking and expects the IO dispatcher.
 */
private class DictationRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    val mime: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "audio/ogg" else "audio/mp4"

    /** The name the recording travels under: the server reads the type, and a file wants a name. */
    val filename: String
        get() = "$DICTATION_NAME.$extension"

    private val extension: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "ogg" else "m4a"

    fun begin(): Boolean = runCatching {
        discard()
        val dir = File(context.cacheDir, "mission_dictation").apply { mkdirs() }
        val file = File(dir, "dictation_${System.currentTimeMillis()}.$extension")
        val fresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        fresh.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            } else {
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            setAudioSamplingRate(SAMPLING_RATE_HZ)
            setAudioChannels(1)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = fresh
        output = file
        true
    }.getOrElse {
        discard()
        false
    }

    fun stop(): ByteArray? = runCatching {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
        val bytes = output?.readBytes()
        output?.delete()
        output = null
        bytes
    }.getOrElse {
        discard()
        null
    }

    fun discard() {
        runCatching {
            recorder?.apply {
                stop()
                release()
            }
        }
        recorder = null
        output?.delete()
        output = null
    }
}

private const val SAMPLING_RATE_HZ = 16_000
private const val DICTATION_NAME = "dictee"

/** Butler is spoken to in French; the scheduler's transcription, which guesses, is the way out. */
private const val DICTATION_LANGUAGE = "fr-FR"

/** Stretches of silence (about five seconds each) after which the phone stops listening by itself. */
private const val MAX_SILENT_RESTARTS = 2

/** The recognizer has no French: `ERROR_LANGUAGE_NOT_SUPPORTED` and `ERROR_LANGUAGE_UNAVAILABLE` (API 31). */
private val LANGUAGE_ERRORS = setOf(12, 13)
