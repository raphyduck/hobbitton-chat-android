package com.garfiec.librechat.feature.tasks.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
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
import java.io.IOException

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
 * The phone's own speech recognition, on the device only (19/10/2026): Android's on-device
 * recognizer, never the network one — that one sends the voice to Google, which is worse than
 * sending it to our own scheduler.
 *
 * Since 26/10/2026 (Raphaël: « il faut absolument qu'il écoute sans s'arrêter jusqu'à ce que
 * j'appuie sur le bouton STOP ») the mic is ours, not the recognizer's: a [DictationMic] records
 * from tap to tap and the recognizer reads it through a pipe ([RecognizerIntent.EXTRA_AUDIO_SOURCE],
 * Android 13), asked for a segmented session. A recognizer that honours it hands over one stretch
 * of speech at a time ([onSegmentResults]) and goes on reading until the pipe closes at the tap.
 * One that does not ends its session at the first pause ([onResults], or an error of silence) and
 * is started again on a new pipe, fed first with everything the mic heard since the recognizer
 * stopped taking words in ([onEndOfSpeech], [DictationTake]): no word falls in the gap, and
 * silence never ends the dictation. Before Android 13 the recognizer would hold the mic itself and
 * stop at pauses, so [available] is false there and the take goes to the scheduler, which never
 * cuts either. Every method runs on the main thread.
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
    private var mic: DictationMic? = null
    private var wanted = false
    private var ended = true

    /** The read end of the session's pipe, ours to close once the recognizer has been handed it. */
    private var reading: ParcelFileDescriptor? = null

    /** The clock when the recognizer stopped taking words in; the next session replays from there. */
    private var quietSince: Long? = null

    fun start(): Boolean = runCatching {
        wanted = true
        ended = false
        quietSince = null
        mic = DictationMic().also { it.start() }
        listen()
        true
    }.getOrElse {
        wanted = false
        mic?.close()
        mic = null
        false
    }

    /** Asks for the end: the words being heard still arrive, then [onEnd]. */
    fun stop() {
        wanted = false
        recognizer?.stopListening()
        // The stream ends: a segmented recognizer finishes its last stretch on that.
        mic?.stopCapture()
    }

    fun release() {
        wanted = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        mic?.close()
        mic = null
        closeReading()
    }

    /** One recognition session on a fresh pipe, fed what the mic heard since the last one quietened. */
    private fun listen() {
        val current = recognizer ?: create().also {
            it.setRecognitionListener(this)
            recognizer = it
        }
        val pipe = ParcelFileDescriptor.createPipe()
        val replayFrom = quietSince?.let { it - REPLAY_MARGIN_NANOS }
        quietSince = null
        checkNotNull(mic).feed(pipe[1], replayFrom)
        // The recognizer is handed the read end later, on the loop (SpeechRecognizer queues the
        // intent until its service answers): ours stays open until the session is over.
        closeReading()
        reading = pipe[0]
        current.startListening(intent(pipe[0]))
    }

    private fun closeReading() {
        reading?.let { runCatching { it.close() } }
        reading = null
    }

    private fun create(): SpeechRecognizer =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            error("No continuous on-device recognition before Android 13")
        }

    private fun intent(source: ParcelFileDescriptor): Intent = intent().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, source)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLING_RATE_HZ)
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        }
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
        if (ended) return
        ended = true
        mic?.close()
        mic = null
        closeReading()
        onEnd(error)
    }

    private fun heard(bundle: Bundle?) {
        val text = firstOf(bundle)
        if (!text.isNullOrBlank()) onHeard(text)
        onPartial("")
    }

    override fun onPartialResults(partialResults: Bundle?) {
        onPartial(firstOf(partialResults).orEmpty())
    }

    /** A stretch of speech, from a recognizer that keeps reading the pipe: nothing to restart. */
    override fun onSegmentResults(segmentResults: Bundle) {
        quietSince = null
        heard(segmentResults)
    }

    /** The pipe closed at the tap; or the recognizer ended on its own, and listens again. */
    override fun onEndOfSegmentedSession() {
        if (wanted) again() else end(null)
    }

    /** The whole session's words, from a recognizer that ends at a pause: it is started again. */
    override fun onResults(results: Bundle?) {
        heard(results)
        if (wanted) again() else end(null)
    }

    override fun onEndOfSpeech() {
        if (quietSince == null) quietSince = System.nanoTime()
    }

    override fun onError(error: Int) {
        val silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        when {
            error in LANGUAGE_ERRORS -> {
                // Asks the phone to fetch its French, for the next dictation.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runCatching { recognizer?.triggerModelDownload(intent()) }
                }
                end(error)
            }
            // The person tapped stop: whatever the recognizer says now, the dictation simply ends.
            !wanted -> end(null)
            // A pause is not the end: the person's tap is.
            silence -> again()
            else -> end(error)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun firstOf(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    companion object {
        fun available(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
    }
}

/**
 * The dictation's mic: an [AudioRecord] read on its own thread from [start] to [stopCapture],
 * 16 kHz mono 16-bit, the source Android gives speech recognition. Each chunk goes into the
 * [DictationTake] and, through an [outgoing] queue, to the pipe the recognizer is reading
 * ([feed]). The pipe's write end is non-blocking and holds 64 KB, two seconds of audio: the queue
 * is what lets a replay longer than that, or a recognizer slow to read, lose nothing, and a
 * recognizer that has stopped reading never stalls the mic. The thread drains the queue and
 * closes the write end once the mic is stopped: for a segmented recognizer, the end of the
 * dictation.
 */
private class DictationMic {
    private val take = DictationTake(TAKE_KEPT_NANOS)
    private val lock = Any()
    private var sink: ParcelFileDescriptor? = null

    /** What the sink has not taken yet, oldest first; [headOffset] bytes of the head are written. */
    private val outgoing = ArrayDeque<ByteArray>()
    private var headOffset = 0
    private var outgoingBytes = 0
    private var record: AudioRecord? = null

    @Volatile
    private var running = false
    private var thread: Thread? = null

    /** The composable asked for RECORD_AUDIO before the tap could start anything (see `toggle`). */
    @SuppressLint("MissingPermission")
    fun start() {
        val minimum = AudioRecord.getMinBufferSize(SAMPLING_RATE_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val fresh = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLING_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum, SAMPLING_RATE_HZ * 2),
        )
        check(fresh.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord not initialised" }
        fresh.startRecording()
        check(fresh.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "AudioRecord not recording" }
        record = fresh
        running = true
        thread = Thread({ pump(fresh) }, "eva-dictation-mic").also { it.start() }
    }

    /** The recognizer's new pipe, fed first with the take heard since [replayFrom] when given. */
    fun feed(write: ParcelFileDescriptor, replayFrom: Long?) {
        // Always true here: the mic only runs where PhoneDictation is available (Android 13).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val flags = Os.fcntlInt(write.fileDescriptor, OsConstants.F_GETFL, 0)
            Os.fcntlInt(write.fileDescriptor, OsConstants.F_SETFL, flags or OsConstants.O_NONBLOCK)
        }
        synchronized(lock) {
            closeSink()
            sink = write
            if (replayFrom != null) take.since(replayFrom).forEach(::enqueue)
            flush()
        }
    }

    /** Stops the mic; the thread then drains the queue and closes the pipe, the end of the stream. */
    fun stopCapture() {
        running = false
        runCatching { record?.stop() }
    }

    fun close() {
        stopCapture()
        thread?.let { runCatching { it.join(JOIN_MS) } }
        thread = null
        synchronized(lock) { closeSink() }
        runCatching { record?.release() }
        record = null
        take.clear()
    }

    private fun pump(source: AudioRecord) {
        val buffer = ByteArray(MIC_CHUNK_BYTES)
        while (running) {
            val read = source.read(buffer, 0, buffer.size)
            if (read < 0) break
            if (read == 0) continue
            val chunk = buffer.copyOf(read)
            take.add(System.nanoTime(), chunk)
            synchronized(lock) {
                if (sink != null) {
                    enqueue(chunk)
                    flush()
                }
            }
        }
        drainAndClose()
    }

    /** Gives the recognizer up to [DRAIN_MS] to take the last words, then ends its stream. */
    private fun drainAndClose() {
        val deadline = System.currentTimeMillis() + DRAIN_MS
        while (System.currentTimeMillis() < deadline) {
            val done = synchronized(lock) {
                flush()
                sink == null || outgoing.isEmpty()
            }
            if (done) break
            runCatching { Thread.sleep(DRAIN_STEP_MS) }
        }
        synchronized(lock) { closeSink() }
    }

    /** Under [lock]. The queue never outgrows the take: a reader gone for that long has lost it anyway. */
    private fun enqueue(chunk: ByteArray) {
        outgoing.addLast(chunk)
        outgoingBytes += chunk.size
        while (outgoingBytes > OUTGOING_MAX_BYTES && outgoing.size > 1) {
            outgoingBytes -= outgoing.removeFirst().size
            headOffset = 0
        }
    }

    /** Under [lock]. Writes what the pipe takes, in order, and stops at the first chunk it does not take whole. */
    private fun flush() {
        val fd = sink?.fileDescriptor ?: return
        while (outgoing.isNotEmpty()) {
            val head = outgoing.first()
            val written = try {
                Os.write(fd, head, headOffset, head.size - headOffset)
            } catch (e: ErrnoException) {
                // A full pipe waits for its reader; a closed one (EPIPE) has none left.
                if (e.errno != OsConstants.EAGAIN) closeSink()
                return
            } catch (_: IOException) {
                return
            }
            if (written <= 0) return
            headOffset += written
            if (headOffset == head.size) {
                outgoing.removeFirst()
                outgoingBytes -= head.size
                headOffset = 0
            }
        }
    }

    /** Under [lock]. */
    private fun closeSink() {
        sink?.let { runCatching { it.close() } }
        sink = null
        outgoing.clear()
        outgoingBytes = 0
        headOffset = 0
    }

    private companion object {
        const val JOIN_MS = 1_500L
        const val DRAIN_MS = 1_000L
        const val DRAIN_STEP_MS = 20L
        const val OUTGOING_MAX_BYTES = SAMPLING_RATE_HZ * 2 * 30
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
            // Tuned for a recognizer, not a voice memo (26/10/2026): the source Android gives
            // speech recognition (no voice-call processing), and an explicit bitrate rather than
            // the encoder's thin default; the server's Whisper hears the difference.
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            } else {
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            setAudioSamplingRate(SAMPLING_RATE_HZ)
            setAudioEncodingBitRate(ENCODING_BIT_RATE)
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
private const val ENCODING_BIT_RATE = 32_000
private const val DICTATION_NAME = "dictee"

/** Eva is spoken to in French; the scheduler's transcription, which guesses, is the way out. */
private const val DICTATION_LANGUAGE = "fr-FR"

/** How much of the take the mic keeps for a recognizer that has to be started again. */
private const val TAKE_KEPT_NANOS = 30_000_000_000L

/**
 * Replayed from a little before the recognizer's end of speech: its callback comes after the
 * moment it stopped taking words in, and that moment sits in a silence anyway.
 */
private const val REPLAY_MARGIN_NANOS = 500_000_000L

/** One read of the mic: 100 ms of 16 kHz mono 16-bit audio. */
private const val MIC_CHUNK_BYTES = SAMPLING_RATE_HZ / 10 * 2

/** The recognizer has no French: `ERROR_LANGUAGE_NOT_SUPPORTED` and `ERROR_LANGUAGE_UNAVAILABLE` (API 31). */
private val LANGUAGE_ERRORS = setOf(12, 13)
