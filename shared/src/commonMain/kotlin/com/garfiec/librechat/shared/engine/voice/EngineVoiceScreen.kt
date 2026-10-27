package com.garfiec.librechat.shared.engine.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.shared.resources.Res
import com.garfiec.librechat.shared.resources.engine_settings_back
import com.garfiec.librechat.shared.resources.engine_voice_ended
import com.garfiec.librechat.shared.resources.engine_voice_failed
import com.garfiec.librechat.shared.resources.engine_voice_hang_up
import com.garfiec.librechat.shared.resources.engine_voice_listening
import com.garfiec.librechat.shared.resources.engine_voice_mic_denied
import com.garfiec.librechat.shared.resources.engine_voice_opening
import com.garfiec.librechat.shared.resources.engine_voice_retry
import com.garfiec.librechat.shared.resources.engine_voice_speaking
import com.garfiec.librechat.shared.resources.engine_voice_thinking
import com.garfiec.librechat.shared.resources.engine_voice_title
import com.garfiec.librechat.shared.resources.engine_voice_unconfigured
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The voice mode (27/10/2026): one screen, the agent's state in large type, its last sentence
 * under it, a red button to hang up. It joins as it opens (the microphone asked first) and leaves
 * as it closes. The agent is the platform's voice agent, at the titulaire's level: what it can do
 * is the server's affair, this screen only carries the voice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineVoiceScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EngineVoiceViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val microphone = rememberMicrophoneGate()
    var micDenied by remember { mutableStateOf(false) }
    val start = { microphone.request(onGranted = { micDenied = false; viewModel.start() }, onDenied = { micDenied = true }) }
    LaunchedEffect(Unit) { start() }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.engine_voice_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.engine_settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    micDenied -> Text(
                        stringResource(Res.string.engine_voice_mic_denied),
                        textAlign = TextAlign.Center,
                    )
                    state.serverUnconfigured -> Text(
                        stringResource(Res.string.engine_voice_unconfigured),
                        textAlign = TextAlign.Center,
                    )
                    state.error != null && state.phase != EngineVoiceUiState.Phase.ENDED -> Text(
                        stringResource(Res.string.engine_voice_failed, state.error.orEmpty()),
                        textAlign = TextAlign.Center,
                    )
                    state.phase == EngineVoiceUiState.Phase.OPENING -> {
                        CircularProgressIndicator()
                        Text(stringResource(Res.string.engine_voice_opening), style = MaterialTheme.typography.titleLarge)
                    }
                    state.phase == EngineVoiceUiState.Phase.LIVE -> {
                        Text(
                            text = when (state.agentState) {
                                "thinking" -> stringResource(Res.string.engine_voice_thinking)
                                "speaking" -> stringResource(Res.string.engine_voice_speaking)
                                else -> stringResource(Res.string.engine_voice_listening)
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("voice_state"),
                        )
                        if (state.heardText.isNotBlank()) {
                            Text(
                                text = state.heardText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (state.agentText.isNotBlank()) {
                            Text(
                                text = state.agentText,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    state.phase == EngineVoiceUiState.Phase.ENDED -> Text(
                        stringResource(Res.string.engine_voice_ended),
                        textAlign = TextAlign.Center,
                    )
                }
                val busy = state.phase == EngineVoiceUiState.Phase.OPENING || state.phase == EngineVoiceUiState.Phase.LIVE
                val canRetry = micDenied || !busy
                if (canRetry && !state.serverUnconfigured) {
                    TextButton(onClick = start) { Text(stringResource(Res.string.engine_voice_retry)) }
                }
            }
            FilledIconButton(
                onClick = {
                    viewModel.stop()
                    onClose()
                },
                modifier = Modifier.size(72.dp).testTag("voice_hang_up"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(
                    Icons.Filled.CallEnd,
                    contentDescription = stringResource(Res.string.engine_voice_hang_up),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}
