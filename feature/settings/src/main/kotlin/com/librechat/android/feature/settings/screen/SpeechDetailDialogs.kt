package com.librechat.android.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.librechat.android.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SttDetailDialog(
    selectedEngine: String,
    selectedLanguage: String,
    availableEngines: List<String>,
    availableLanguages: List<String>,
    onConfirm: (engine: String, language: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var engine by remember { mutableStateOf(selectedEngine) }
    var language by remember { mutableStateOf(selectedLanguage) }
    var engineExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.speech_to_text_settings)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Engine selector
                Text(
                    text = stringResource(R.string.stt_engine_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                ExposedDropdownMenuBox(
                    expanded = engineExpanded,
                    onExpandedChange = { engineExpanded = it },
                ) {
                    OutlinedTextField(
                        value = when {
                            engine.equals("Device", ignoreCase = true) -> stringResource(R.string.stt_on_device)
                            engine.isBlank() -> stringResource(R.string.stt_default)
                            else -> engine
                        },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = engineExpanded,
                        onDismissRequest = { engineExpanded = false },
                    ) {
                        // On-device option first, separated by a divider
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.stt_on_device)) },
                            onClick = {
                                engine = "Device"
                                engineExpanded = false
                            },
                        )
                        HorizontalDivider()
                        // Server-related options
                        availableEngines.filter { !it.equals("Device", ignoreCase = true) }.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    engine = item
                                    engineExpanded = false
                                },
                            )
                        }
                    }
                }

                // Engine-specific hint
                if (engine.equals("Whisper", ignoreCase = true)) {
                    Text(
                        text = stringResource(R.string.stt_hint_whisper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (engine.equals("Device", ignoreCase = true)) {
                    Text(
                        text = stringResource(R.string.stt_hint_device),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (engine.equals("Google", ignoreCase = true)) {
                    Text(
                        text = stringResource(R.string.stt_hint_google),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (engine.equals("Default", ignoreCase = true) || engine.isBlank()) {
                    Text(
                        text = stringResource(R.string.stt_hint_default),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Language selector
                Text(
                    text = stringResource(R.string.stt_language_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                ExposedDropdownMenuBox(
                    expanded = languageExpanded,
                    onExpandedChange = { languageExpanded = it },
                ) {
                    OutlinedTextField(
                        value = language.ifBlank { stringResource(R.string.stt_auto_detect) },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = languageExpanded,
                        onDismissRequest = { languageExpanded = false },
                    ) {
                        availableLanguages.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    language = item
                                    languageExpanded = false
                                },
                            )
                        }
                    }
                }

            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(engine, language) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

data class DeviceVoiceInfo(
    val name: String,
    val locale: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TtsDetailDialog(
    selectedEngine: String,
    selectedVoice: String,
    speechRate: Float,
    pitch: Float,
    deviceVoiceName: String,
    cachingEnabled: Boolean,
    ttsSource: String,
    availableEngines: List<String>,
    availableVoices: List<String>,
    availableDeviceVoices: List<DeviceVoiceInfo>,
    isPreviewPlaying: Boolean = false,
    onPreviewDevice: (text: String, rate: Float, pitch: Float, voiceName: String?) -> Unit = { _, _, _, _ -> },
    onPreviewServer: (text: String, voice: String?, model: String?) -> Unit = { _, _, _ -> },
    onStopPreview: () -> Unit = {},
    onConfirm: (engine: String, voice: String, rate: Float, pitch: Float, deviceVoiceName: String, caching: Boolean, source: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var engine by remember { mutableStateOf(selectedEngine) }
    var voice by remember { mutableStateOf(selectedVoice) }
    var rate by remember { mutableFloatStateOf(speechRate) }
    var currentPitch by remember { mutableFloatStateOf(pitch) }
    var currentDeviceVoice by remember { mutableStateOf(deviceVoiceName) }
    var caching by remember { mutableStateOf(cachingEnabled) }
    var source by remember { mutableStateOf(ttsSource) }
    var engineExpanded by remember { mutableStateOf(false) }
    var voiceExpanded by remember { mutableStateOf(false) }
    var sourceExpanded by remember { mutableStateOf(false) }
    var deviceVoiceExpanded by remember { mutableStateOf(false) }

    val deviceLabel = stringResource(R.string.tts_source_device)
    val serverLabel = stringResource(R.string.tts_source_server)
    val sourceOptions = listOf("device" to deviceLabel, "server" to serverLabel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_to_speech_settings)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // TTS Source selector
                Text(
                    text = stringResource(R.string.tts_source_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                ExposedDropdownMenuBox(
                    expanded = sourceExpanded,
                    onExpandedChange = { sourceExpanded = it },
                ) {
                    OutlinedTextField(
                        value = sourceOptions.firstOrNull { it.first == source }?.second ?: deviceLabel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = sourceExpanded,
                        onDismissRequest = { sourceExpanded = false },
                    ) {
                        sourceOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    source = value
                                    sourceExpanded = false
                                },
                            )
                        }
                    }
                }

                if (source == "device") {
                    // Device voice picker
                    if (availableDeviceVoices.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.tts_voice_label),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        val selectedDisplayName = availableDeviceVoices
                            .find { it.name == currentDeviceVoice }
                            ?.let { "${it.name} (${it.locale})" }
                            ?: stringResource(R.string.tts_system_default)
                        ExposedDropdownMenuBox(
                            expanded = deviceVoiceExpanded,
                            onExpandedChange = { deviceVoiceExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = selectedDisplayName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceVoiceExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(
                                expanded = deviceVoiceExpanded,
                                onDismissRequest = { deviceVoiceExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.tts_system_default)) },
                                    onClick = {
                                        currentDeviceVoice = ""
                                        deviceVoiceExpanded = false
                                    },
                                )
                                availableDeviceVoices.forEach { dv ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = dv.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                                Text(
                                                    text = dv.locale,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            currentDeviceVoice = dv.name
                                            deviceVoiceExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Speech rate slider
                    Text(
                        text = stringResource(R.string.tts_speech_rate, rate),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = rate,
                        onValueChange = { rate = it },
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Pitch slider
                    Text(
                        text = stringResource(R.string.tts_pitch, currentPitch),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = currentPitch,
                        onValueChange = { currentPitch = it },
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (source == "server") {
                    // Engine selector (only for server TTS)
                    Text(
                        text = stringResource(R.string.stt_engine_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    ExposedDropdownMenuBox(
                        expanded = engineExpanded,
                        onExpandedChange = { engineExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = engine.ifBlank { stringResource(R.string.stt_default) },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = engineExpanded,
                            onDismissRequest = { engineExpanded = false },
                        ) {
                            availableEngines.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        engine = item
                                        engineExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    // Voice selector (only for server TTS)
                    Text(
                        text = stringResource(R.string.tts_voice_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    ExposedDropdownMenuBox(
                        expanded = voiceExpanded,
                        onExpandedChange = { voiceExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = voice.ifBlank { stringResource(R.string.stt_default) },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = voiceExpanded,
                            onDismissRequest = { voiceExpanded = false },
                        ) {
                            availableVoices.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        voice = item
                                        voiceExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    // Caching toggle (only for server TTS)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.tts_cache_audio),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = caching,
                            onCheckedChange = { caching = it },
                        )
                    }
                }

                // Preview button
                val previewText = stringResource(R.string.tts_preview_text)
                val previewCd = stringResource(if (isPreviewPlaying) R.string.cd_stop_voice_preview else R.string.cd_preview_voice)
                FilledTonalButton(
                    onClick = {
                        if (isPreviewPlaying) {
                            onStopPreview()
                        } else {
                            if (source == "device") {
                                onPreviewDevice(
                                    previewText,
                                    rate,
                                    currentPitch,
                                    currentDeviceVoice.ifBlank { null },
                                )
                            } else {
                                onPreviewServer(
                                    previewText,
                                    voice.ifBlank { null },
                                    engine.ifBlank { null },
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = previewCd
                        },
                ) {
                    Icon(
                        imageVector = if (isPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(if (isPreviewPlaying) R.string.tts_stop_preview else R.string.tts_preview_voice))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onStopPreview()
                onConfirm(engine, voice, rate, currentPitch, currentDeviceVoice, caching, source)
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onStopPreview()
                onDismiss()
            }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
