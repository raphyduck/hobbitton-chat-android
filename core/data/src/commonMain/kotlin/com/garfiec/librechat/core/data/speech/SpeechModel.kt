package com.garfiec.librechat.core.data.speech

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The dictation models the phone runs itself (11/11/2026, Raphaël: « On va tout faire sur le
 * téléphone », Android's own recognizer heard French far worse than Claude's app). Each is too
 * large to ship in the APK: downloaded once from Settings → Dictée, over Wi-Fi, each file checked
 * against its SHA-256 before it is used; the voice never leaves the phone. Every export is pinned
 * to one revision: a file changed upstream is not ours.
 *
 * Three since 20/11/2026 (Raphaël: Parakeet « mélange anglais et français allègrement », then
 * « essaie Canary … Ajoute le Canary Flash et pas Flash et je testerai »): Parakeet guesses the
 * language of each stretch of speech and nothing can fix it; NVIDIA's Canary models are told the
 * language they hear and the one they write, French for both here, so their output is French
 * whatever was said.
 */
object SpeechModels {
    private const val VAD_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"

    /** Silero's voice detector, which cuts the take into sentences: each model keeps its copy. */
    private val VAD = SpeechModelFile(
        name = "silero_vad.onnx",
        url = VAD_URL,
        bytes = 643_854,
        sha256 = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
    )

    /** Parakeet TDT 0.6B v3: 25 European languages, the language guessed; sherpa-onnx's int8 export. */
    val PARAKEET: SpeechModel = run {
        val base = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8" +
            "/resolve/ed5d745623472f97cfc2e6c2916ee248798fc5a8"
        SpeechModel(
            id = "parakeet-tdt-0.6b-v3",
            name = "Parakeet TDT 0.6B v3",
            directory = "speech/parakeet-tdt-0.6b-v3-int8",
            kind = SpeechModelKind.TRANSDUCER,
            encoder = SpeechModelFile(
                name = "encoder.int8.onnx",
                url = "$base/encoder.int8.onnx",
                bytes = 652_282_294,
                sha256 = "1afbe69c1ffdabee94755f42e4daf1d93549131a3da11356bda84fcef0f2c479",
            ),
            decoder = SpeechModelFile(
                name = "decoder.int8.onnx",
                url = "$base/decoder.int8.onnx",
                bytes = 11_845_274,
                sha256 = "0222a86147299d4dee90edf95b6d13827edb42ad8b7c9cf0b2582f030937c0b5",
            ),
            joiner = SpeechModelFile(
                name = "joiner.int8.onnx",
                url = "$base/joiner.int8.onnx",
                bytes = 6_355_277,
                sha256 = "cbd02f3696e9944970111fb439d9c3fd365a80374565f13f2b9c7007d253fc98",
            ),
            tokens = SpeechModelFile(
                name = "tokens.txt",
                url = "$base/tokens.txt",
                bytes = 93_939,
                sha256 = "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d",
            ),
            vad = VAD,
        )
    }

    /** Canary 180M Flash: English, Spanish, German, French; small and quick. sherpa-onnx's int8 export. */
    val CANARY_FLASH: SpeechModel = run {
        val base = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8" +
            "/resolve/9077164e0d3dd1d5353743e89ceaa1d3a770838c"
        SpeechModel(
            id = "canary-180m-flash",
            name = "Canary 180M Flash",
            directory = "speech/canary-180m-flash-int8",
            kind = SpeechModelKind.CANARY,
            encoder = SpeechModelFile(
                name = "encoder.int8.onnx",
                url = "$base/encoder.int8.onnx",
                bytes = 132_678_643,
                sha256 = "7a75b4e2a5857a6dcc0819503bbe3fad66943db4a3ccf21d3f27c633667d303f",
            ),
            decoder = SpeechModelFile(
                name = "decoder.int8.onnx",
                url = "$base/decoder.int8.onnx",
                bytes = 74_437_848,
                sha256 = "e41a2ab9c0c2fe81a1e8ade5a45fb02a74bc4db7d1f91b89a54a25e2cf79cba2",
            ),
            joiner = null,
            tokens = SpeechModelFile(
                name = "tokens.txt",
                url = "$base/tokens.txt",
                bytes = 53_555,
                sha256 = "2dae6fc7815f9640645e0c765522b278ee0cef49b482d91f6913e334628d3e77",
            ),
            vad = VAD,
        )
    }

    /**
     * Canary 1B v2: 25 European languages, the most accurate of the three on French by NVIDIA's
     * own figures, and the heaviest. Neither NVIDIA nor the sherpa-onnx project publishes its
     * ONNX export: this one is a community export (bnoordhuis, CC-BY-4.0, made for Android),
     * taken at one revision and by SHA-256 like the others.
     */
    val CANARY_V2: SpeechModel = run {
        val base = "https://huggingface.co/bnoordhuis/canary-1b-v2-sherpa-onnx-nl-int8" +
            "/resolve/28a965808e76ab3182c10591338a0f7914d59c0d"
        SpeechModel(
            id = "canary-1b-v2",
            name = "Canary 1B v2",
            directory = "speech/canary-1b-v2-int8",
            kind = SpeechModelKind.CANARY,
            encoder = SpeechModelFile(
                name = "encoder.int8.onnx",
                url = "$base/encoder.int8.onnx",
                bytes = 1_161_643_089,
                sha256 = "8342623508dcf97c81d48f930655bb26aeb05bae7ae9336cd0001e6f5f544888",
            ),
            decoder = SpeechModelFile(
                name = "decoder.int8.onnx",
                url = "$base/decoder.int8.onnx",
                bytes = 223_741_759,
                sha256 = "ac51ba4975dc058013b041142981a376105874eb267db8a5817952154a3b7e11",
            ),
            joiner = null,
            tokens = SpeechModelFile(
                name = "tokens.txt",
                url = "$base/tokens.txt",
                bytes = 208_022,
                sha256 = "2c9efe6104fd29522ea27ce0e3aef5d37c690af4e5a4232e643e23ca403ffea3",
            ),
            vad = VAD,
        )
    }

    /** In the order Settings lists them; the first one installed serves until another is chosen. */
    val ALL: List<SpeechModel> = listOf(PARAKEET, CANARY_FLASH, CANARY_V2)
}

/** How sherpa-onnx reads a model. */
enum class SpeechModelKind {
    /** Encoder, decoder and joiner (Parakeet TDT): the language is the model's guess. */
    TRANSDUCER,

    /** Encoder and decoder (Canary): the languages heard and written are given to it. */
    CANARY,
}

/**
 * One dictation model: its files, where they sit under the app's external files directory
 * ([directory]) and how the recognizer reads them ([kind]).
 */
data class SpeechModel(
    val id: String,
    val name: String,
    val directory: String,
    val kind: SpeechModelKind,
    val encoder: SpeechModelFile,
    val decoder: SpeechModelFile,
    val joiner: SpeechModelFile?,
    val tokens: SpeechModelFile,
    val vad: SpeechModelFile,
) {
    val files: List<SpeechModelFile> get() = listOfNotNull(encoder, decoder, joiner, tokens, vad)

    /** What the download weighs, all files together. */
    val totalBytes: Long get() = files.sumOf { it.bytes }
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
    val model: SpeechModel,
    val encoder: String,
    val decoder: String,
    val joiner: String?,
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
     *   spends the mobile plan on hundreds of megabytes.
     */
    data class Downloading(val done: Long, val total: Long, val waitingForWifi: Boolean = false) : SpeechModelState {
        val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    }

    /** Every file is down; their hashes are being checked. */
    data object Verifying : SpeechModelState

    data class Ready(val paths: SpeechModelPaths) : SpeechModelState

    data class Failed(val failure: SpeechModelFailure) : SpeechModelState
}

/** One model's download, kept by the platform (Android's DownloadManager): it survives the app. */
interface SpeechModelStore {
    val model: SpeechModel

    val state: StateFlow<SpeechModelState>

    /** The phone can run the model: a 64-bit one, the only kind the app carries its libraries for. */
    val supported: Boolean

    /** Starts the download (Wi-Fi only); a failure or an absent model starts over. */
    fun download()

    /** Stops a download in progress and throws away what came. */
    fun cancel()

    /** Removes the model from the phone. */
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

/**
 * The models of this phone and the one the dictation goes through (20/11/2026): the one the
 * person chose in Settings → Dictée when it is installed, else the first installed in the list's
 * order, else none and Android's recognizer hears. The choice is kept by [remember].
 */
class SpeechModelLibrary(
    val stores: List<SpeechModelStore>,
    private val remember: (String) -> Unit = {},
) {
    private val _chosen = MutableStateFlow<String?>(null)
    val chosen: StateFlow<String?> = _chosen.asStateFlow()

    /** The phone can run the models. */
    val supported: Boolean get() = stores.any { it.supported }

    /** The person's pick; a model still downloading serves once it is there. */
    fun choose(id: String) {
        _chosen.value = id
        remember(id)
    }

    /** The choice read back from the device at start, unless the person chose since. */
    fun restore(id: String?) {
        _chosen.compareAndSet(null, id)
    }

    /** What a dictation starting now is heard by, read at the tap: never a stale copy. */
    fun active(): SpeechModelPaths? = activeSpeechModel(_chosen.value, stores.map { it.state.value })
}

/** The installed model the dictation goes through: the chosen one, else the first installed. */
fun activeSpeechModel(chosen: String?, states: List<SpeechModelState>): SpeechModelPaths? {
    val installed = states.filterIsInstance<SpeechModelState.Ready>().map { it.paths }
    return installed.firstOrNull { it.model.id == chosen } ?: installed.firstOrNull()
}
