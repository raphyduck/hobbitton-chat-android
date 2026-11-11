package com.garfiec.librechat.feature.tasks.components

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.speech.SpeechModelPaths
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The dictation heard by Parakeet TDT 0.6B v3 on the phone itself (11/11/2026, Raphaël: « On va
 * tout faire sur le téléphone »; Android's own recognizer heard French far worse than Claude's
 * app). The model is the one Settings → Dictée downloaded ([SpeechModelPaths]); nothing leaves the
 * device.
 *
 * Three hands: a mic thread reads an [AudioRecord] (16 kHz mono, the speech-recognition source)
 * from tap to tap into a queue, never blocked by the decoding; a worker thread feeds the queue to
 * Silero's voice detector, which cuts the take at each pause, and decodes every stretch of speech
 * it hands over with Parakeet ([onHeard], one sentence or so at a time, punctuated and
 * capitalised by the model); while a stretch is still being spoken, the worker decodes what it has
 * of it now and then for the line under the composer ([onPartial]), when it is not behind. Silence
 * never ends the dictation: the tap does, after the last stretch has been decoded ([onEnd]).
 *
 * The recognizer (the model in memory, a few seconds to load) is kept between dictations by
 * [ParakeetEngine] and let go after a few idle minutes. Callbacks come on the main thread.
 *
 * @param onEnd the dictation is over: null for a normal end, else a short reason for the journal.
 */
internal class ParakeetDictation(
    private val paths: SpeechModelPaths,
    private val onPartial: (String) -> Unit,
    private val onHeard: (String) -> Unit,
    private val onEnd: (error: String?) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val queue = LinkedBlockingQueue<FloatArray>()

    @Volatile
    private var capturing = false

    @Volatile
    private var released = false
    private var record: AudioRecord? = null

    /** The composable asked for RECORD_AUDIO before the tap could start anything (see `toggle`). */
    @SuppressLint("MissingPermission")
    fun start(): Boolean = runCatching {
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val fresh = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum, SAMPLE_RATE * 2),
        )
        check(fresh.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord not initialised" }
        fresh.startRecording()
        check(fresh.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "AudioRecord not recording" }
        record = fresh
        capturing = true
        Thread({ pump(fresh) }, "eva-parakeet-mic").start()
        Thread(::work, "eva-parakeet").start()
        true
    }.getOrElse {
        Logger.w(it, tag = TAG) { "Could not start the mic" }
        capturing = false
        record?.release()
        record = null
        false
    }

    /** Asks for the end: what was said is still decoded, then [onEnd]. */
    fun stop() {
        capturing = false
        runCatching { record?.stop() }
    }

    /** The screen is gone: nothing more is delivered. */
    fun release() {
        released = true
        stop()
        main.removeCallbacksAndMessages(null)
    }

    private fun pump(source: AudioRecord) {
        val buffer = ShortArray(SAMPLE_RATE / 10)
        try {
            while (capturing) {
                val read = source.read(buffer, 0, buffer.size)
                if (read < 0) break
                if (read > 0) queue.put(FloatArray(read) { buffer[it] / PCM_SCALE })
            }
        } finally {
            runCatching { source.release() }
            queue.put(END)
        }
    }

    private fun work() {
        var vad: Vad? = null
        val failure = runCatching {
            val recognizer = ParakeetEngine.acquire(paths)
            val detector = Vad(null, vadConfig(paths.vad)).also { vad = it }
            listen(recognizer, detector)
        }.exceptionOrNull()
        if (failure != null) {
            // UnsatisfiedLinkError on a phone the native library was not packaged for, a model
            // file gone, an out-of-memory loading it: the dictation ends and says so.
            Logger.e(failure, tag = TAG) { "Parakeet dictation failed" }
            capturing = false
            runCatching { record?.stop() }
        }
        runCatching { vad?.release() }
        ParakeetEngine.releaseLater()
        queue.clear()
        val error = failure?.let { it::class.simpleName ?: "error" }
        main.post {
            if (!released) {
                onPartial("")
                onEnd(error)
            }
        }
    }

    /** Feeds the take to the voice detector and decodes each stretch it hands over, until the mic's end. */
    private fun listen(recognizer: OfflineRecognizer, vad: Vad) {
        val window = FloatArray(VAD_WINDOW)
        var filled = 0
        val speaking = ArrayList<FloatArray>()
        var speakingSamples = 0
        var sinceGlance = 0
        while (true) {
            val chunk = queue.poll(POLL_SECONDS, TimeUnit.SECONDS) ?: continue
            if (chunk === END) break
            var offset = 0
            while (offset < chunk.size) {
                val take = minOf(VAD_WINDOW - filled, chunk.size - offset)
                chunk.copyInto(window, filled, offset, offset + take)
                filled += take
                offset += take
                if (filled == VAD_WINDOW) {
                    vad.acceptWaveform(window.copyOf())
                    filled = 0
                }
            }
            if (vad.isSpeechDetected()) {
                speaking += chunk
                speakingSamples += chunk.size
                sinceGlance += chunk.size
            }
            var heardOne = false
            while (!vad.empty()) {
                val segment = vad.front()
                vad.pop()
                deliverHeard(decode(recognizer, segment.samples))
                heardOne = true
            }
            if (heardOne) {
                speaking.clear()
                speakingSamples = 0
                sinceGlance = 0
            } else if (sinceGlance >= GLANCE_SAMPLES && queue.size < BEHIND_CHUNKS && speakingSamples <= GLANCE_MAX_SAMPLES) {
                // The stretch still being spoken, decoded as it stands: the line under the box.
                sinceGlance = 0
                deliverPartial(decode(recognizer, join(speaking, speakingSamples)))
            }
        }
        if (filled > 0) vad.acceptWaveform(window.copyOf(filled))
        vad.flush()
        while (!vad.empty()) {
            val segment = vad.front()
            vad.pop()
            deliverHeard(decode(recognizer, segment.samples))
        }
    }

    private fun decode(recognizer: OfflineRecognizer, samples: FloatArray): String {
        if (samples.size < MIN_DECODE_SAMPLES) return ""
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    private fun deliverHeard(text: String) {
        main.post {
            if (released) return@post
            onPartial("")
            if (text.isNotBlank()) onHeard(text)
        }
    }

    private fun deliverPartial(text: String) {
        main.post { if (!released && text.isNotBlank()) onPartial(text) }
    }

    private companion object {
        const val TAG = "ParakeetDictation"
        const val SAMPLE_RATE = 16_000
        const val PCM_SCALE = 32_768f

        /** Silero's window at 16 kHz. */
        const val VAD_WINDOW = 512
        const val POLL_SECONDS = 1L

        /** The line under the box is refreshed every 1,2 s of speech, while the stretch is under 20 s. */
        const val GLANCE_SAMPLES = SAMPLE_RATE * 12 / 10
        const val GLANCE_MAX_SAMPLES = SAMPLE_RATE * 20

        /** More than 1 s of the mic waiting: the partial waits, the sentences come first. */
        const val BEHIND_CHUNKS = 10

        /** Under 0,2 s there is nothing to read. */
        const val MIN_DECODE_SAMPLES = SAMPLE_RATE / 5

        /** The mic's end, put on the queue once the last chunk is in. */
        val END = FloatArray(0)

        fun vadConfig(model: String) = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = model,
                threshold = 0.5f,
                // A pause of 0,6 s closes a stretch; a stretch never runs past 25 s, so a long
                // monologue still comes in pieces the model reads well.
                minSilenceDuration = 0.6f,
                minSpeechDuration = 0.25f,
                windowSize = VAD_WINDOW,
                maxSpeechDuration = 25f,
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = 1,
        )

        fun join(parts: List<FloatArray>, size: Int): FloatArray {
            val out = FloatArray(size)
            var at = 0
            for (part in parts) {
                part.copyInto(out, at)
                at += part.size
            }
            return out
        }
    }
}

/**
 * The recognizer, one per process: loading Parakeet takes a few seconds and some 700 MB, so it is
 * kept between dictations and let go once none has run for [IDLE_MS]. Its calls run on the
 * dictation's worker thread.
 */
internal object ParakeetEngine {
    private const val IDLE_MS = 3 * 60_000L
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private var loaded: Pair<SpeechModelPaths, OfflineRecognizer>? = null
    private val dropIdle = Runnable {
        synchronized(lock) {
            loaded?.second?.release()
            loaded = null
        }
    }

    fun acquire(paths: SpeechModelPaths): OfflineRecognizer = synchronized(lock) {
        main.removeCallbacks(dropIdle)
        loaded?.takeIf { it.first == paths }?.second ?: run {
            loaded?.second?.release()
            val started = System.currentTimeMillis()
            val recognizer = OfflineRecognizer(null, config(paths))
            Logger.i(tag = "ParakeetDictation") { "Parakeet loaded in ${System.currentTimeMillis() - started} ms" }
            loaded = paths to recognizer
            recognizer
        }
    }

    fun releaseLater() {
        main.removeCallbacks(dropIdle)
        main.postDelayed(dropIdle, IDLE_MS)
    }

    private fun config(paths: SpeechModelPaths) = OfflineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
        modelConfig = OfflineModelConfig(
            transducer = OfflineTransducerModelConfig(
                encoder = paths.encoder,
                decoder = paths.decoder,
                joiner = paths.joiner,
            ),
            tokens = paths.tokens,
            modelType = "nemo_transducer",
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
        ),
        decodingMethod = "greedy_search",
    )
}
