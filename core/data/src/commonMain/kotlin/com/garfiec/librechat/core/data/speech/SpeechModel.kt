package com.garfiec.librechat.core.data.speech

import kotlinx.coroutines.flow.StateFlow

/**
 * The dictation model the phone runs itself (11/11/2026, Raphaël: « On va tout faire sur le
 * téléphone », Android's own recognizer heard French far worse than Claude's app): NVIDIA's
 * Parakeet TDT 0.6B v3, 25 European languages French included, in the int8 export the sherpa-onnx
 * project publishes, and the Silero voice detector that cuts the take into sentences. Too large to
 * ship in the APK (671 MB), it is downloaded once from Settings → Dictée, over Wi-Fi, each file
 * checked against its SHA-256 before it is used; the voice never leaves the phone.
 */
object ParakeetModel {
    /** The export's repository and the revision pinned: a file changed upstream is not ours. */
    private const val REPOSITORY = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8"
    private const val REVISION = "ed5d745623472f97cfc2e6c2916ee248798fc5a8"
    private const val VAD_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"

    /** Where the files sit under the app's external files directory. */
    const val DIRECTORY = "speech/parakeet-tdt-0.6b-v3-int8"

    val ENCODER = SpeechModelFile(
        name = "encoder.int8.onnx",
        url = "$REPOSITORY/resolve/$REVISION/encoder.int8.onnx",
        bytes = 652_282_294,
        sha256 = "1afbe69c1ffdabee94755f42e4daf1d93549131a3da11356bda84fcef0f2c479",
    )
    val DECODER = SpeechModelFile(
        name = "decoder.int8.onnx",
        url = "$REPOSITORY/resolve/$REVISION/decoder.int8.onnx",
        bytes = 11_845_274,
        sha256 = "0222a86147299d4dee90edf95b6d13827edb42ad8b7c9cf0b2582f030937c0b5",
    )
    val JOINER = SpeechModelFile(
        name = "joiner.int8.onnx",
        url = "$REPOSITORY/resolve/$REVISION/joiner.int8.onnx",
        bytes = 6_355_277,
        sha256 = "cbd02f3696e9944970111fb439d9c3fd365a80374565f13f2b9c7007d253fc98",
    )
    val TOKENS = SpeechModelFile(
        name = "tokens.txt",
        url = "$REPOSITORY/resolve/$REVISION/tokens.txt",
        bytes = 93_939,
        sha256 = "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d",
    )
    val VAD = SpeechModelFile(
        name = "silero_vad.onnx",
        url = VAD_URL,
        bytes = 643_854,
        sha256 = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
    )

    val FILES: List<SpeechModelFile> = listOf(ENCODER, DECODER, JOINER, TOKENS, VAD)

    /** What the download weighs, all files together. */
    val TOTAL_BYTES: Long = FILES.sumOf { it.bytes }
}

/** One file of the model: where it comes from, and what it must weigh and hash to. */
data class SpeechModelFile(
    val name: String,
    val url: String,
    val bytes: Long,
    val sha256: String,
)

/** The model's files on the phone, once every one of them has been checked. */
data class SpeechModelPaths(
    val encoder: String,
    val decoder: String,
    val joiner: String,
    val tokens: String,
    val vad: String,
)

/** Why the model is not there. */
enum class SpeechModelFailure {
    /** The download stopped on the network's or the server's side. */
    NETWORK,

    /** Not enough room on the phone. */
    STORAGE,

    /** A file arrived but did not hash to what was published: thrown away. */
    CORRUPT,
}

sealed interface SpeechModelState {
    data object Absent : SpeechModelState

    /**
     * @param waitingForWifi Android holds the download until a Wi-Fi network is there: it never
     *   spends the mobile plan on 671 MB.
     */
    data class Downloading(val done: Long, val total: Long, val waitingForWifi: Boolean = false) : SpeechModelState {
        val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    }

    /** Every file is down; their hashes are being checked. */
    data object Verifying : SpeechModelState

    data class Ready(val paths: SpeechModelPaths) : SpeechModelState

    data class Failed(val failure: SpeechModelFailure) : SpeechModelState
}

/** The model's download, kept by the platform (Android's DownloadManager): it survives the app. */
interface SpeechModelStore {
    val state: StateFlow<SpeechModelState>

    /** The phone can run the model: a 64-bit one, the only kind the app carries its libraries for. */
    val supported: Boolean

    /** Starts the download (Wi-Fi only); a failure or an absent model starts over. */
    fun download()

    /** Stops a download in progress and throws away what came. */
    fun cancel()

    /** Removes the model from the phone: the phone's own recognizer hears the dictation again. */
    fun delete()
}

/** How one file of a download stands, as the platform reports it. */
enum class FileDownload { PENDING, RUNNING, WAITING_FOR_WIFI, DONE, FAILED_NETWORK, FAILED_STORAGE, MISSING }

/**
 * The model's state from its files' downloads: a failure wins, then the end once every file is
 * done, else the bytes so far against the known total. A download the platform forgot (the entry
 * removed, the phone wiped its queue) is a network failure: the person starts it again.
 */
fun downloadState(files: List<Pair<FileDownload, Long>>, total: Long): SpeechModelState {
    val statuses = files.map { it.first }
    return when {
        FileDownload.FAILED_STORAGE in statuses -> SpeechModelState.Failed(SpeechModelFailure.STORAGE)
        FileDownload.FAILED_NETWORK in statuses || FileDownload.MISSING in statuses ->
            SpeechModelState.Failed(SpeechModelFailure.NETWORK)
        statuses.isNotEmpty() && statuses.all { it == FileDownload.DONE } -> SpeechModelState.Verifying
        else -> SpeechModelState.Downloading(
            done = files.sumOf { it.second },
            total = total,
            waitingForWifi = FileDownload.WAITING_FOR_WIFI in statuses && FileDownload.RUNNING !in statuses,
        )
    }
}
