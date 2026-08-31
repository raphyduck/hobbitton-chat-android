package com.garfiec.librechat.feature.tasks.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The document picker narrowed to audio, its bytes read off the main thread.
 *
 * The size cap is Whisper's own request limit (25 MB); an over-large file becomes no pick at all
 * rather than an upload that the server will refuse after the whole thing has travelled.
 */
@Composable
internal actual fun rememberMissionAudioPicker(
    onPick: (PickedAudio) -> Unit,
): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    val mime = context.contentResolver.getType(uri) ?: FALLBACK_MIME
                    bytes?.takeIf { it.isNotEmpty() && it.size <= MAX_AUDIO_BYTES }
                        ?.let { PickedAudio(it, mime) }
                }.getOrNull()
            }
            picked?.let(onPick)
        }
    }
    return { launcher.launch(arrayOf("audio/*")) }
}

private const val FALLBACK_MIME = "audio/mpeg"
private const val MAX_AUDIO_BYTES = 25 * 1024 * 1024
