package com.garfiec.librechat.feature.tasks.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MediaRecorder behind a two-state button. The recorder's blocking calls (prepare/start/stop and
 * the file read) run on the IO dispatcher; the composable only flips [MissionDictation.recording].
 */
@Composable
internal actual fun rememberMissionDictation(
    onCapture: (PickedAudio) -> Unit,
): MissionDictation? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { DictationRecorder(context) }
    var recording by remember { mutableStateOf(false) }

    // Leaving the screen mid-recording throws the take away — there is nobody left to stop it for.
    DisposableEffect(recorder) {
        onDispose { recorder.discard() }
    }

    val begin: () -> Unit = {
        scope.launch {
            recording = withContext(Dispatchers.IO) { recorder.begin() }
        }
    }
    val permission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) begin() }

    return MissionDictation(
        recording = recording,
        toggle = {
            if (recording) {
                recording = false
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) { recorder.stop() }
                    bytes?.takeIf { it.isNotEmpty() }
                        ?.let { onCapture(PickedAudio(it, recorder.mime, DICTATION_NAME)) }
                }
            } else {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) begin() else permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
    )
}

/**
 * The chat's recorder choices, restated here because a feature module cannot see another's code:
 * OGG/Opus on API 29+, 3GP/AMR before — Whisper takes both — 16 kHz mono, a cache file that is
 * read once and deleted. Every method is blocking and expects the IO dispatcher.
 */
private class DictationRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    val mime: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "audio/ogg" else "audio/3gpp"

    fun begin(): Boolean = runCatching {
        discard()
        val extension = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "ogg" else "3gp"
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
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
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
private const val DICTATION_NAME = "dictée"
