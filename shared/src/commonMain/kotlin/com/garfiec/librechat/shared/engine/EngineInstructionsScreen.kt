package com.garfiec.librechat.shared.engine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.shared.resources.Res
import com.garfiec.librechat.shared.resources.engine_instructions_cancel
import com.garfiec.librechat.shared.resources.engine_instructions_enabled
import com.garfiec.librechat.shared.resources.engine_instructions_enabled_hint
import com.garfiec.librechat.shared.resources.engine_instructions_hint
import com.garfiec.librechat.shared.resources.engine_instructions_label
import com.garfiec.librechat.shared.resources.engine_instructions_save
import com.garfiec.librechat.shared.resources.engine_instructions_save_failed
import com.garfiec.librechat.shared.resources.engine_instructions_title
import com.garfiec.librechat.shared.resources.engine_settings_back
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The global instructions' editor (D-077), opened from the engine shell's settings. Closes on
 * [onClose] once saved, or at once on Annuler / back — what was typed then is dropped.
 */
@Composable
internal fun EngineInstructionsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EngineInstructionsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)

    LaunchedEffect(state.saved) {
        if (state.saved) close()
    }

    EngineInstructionsContent(
        state = state,
        onInstructions = viewModel::setInstructions,
        onEnabledChange = viewModel::setEnabled,
        onSave = viewModel::save,
        onCancel = onClose,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineInstructionsContent(
    state: EngineInstructionsUiState,
    onInstructions: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.engine_instructions_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.engine_settings_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            InstructionsActions(
                canSave = !state.loading && !state.saving,
                saving = state.saving,
                onSave = onSave,
                onCancel = onCancel,
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            InstructionsForm(
                state = state,
                padding = padding,
                onInstructions = onInstructions,
                onEnabledChange = onEnabledChange,
            )
        }
    }
}

@Composable
private fun InstructionsForm(
    state: EngineInstructionsUiState,
    padding: PaddingValues,
    onInstructions: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.engine_instructions_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = state.enabled, onValueChange = onEnabledChange, role = Role.Switch),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.engine_instructions_enabled), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(Res.string.engine_instructions_enabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.enabled, onCheckedChange = null)
        }
        OutlinedTextField(
            value = state.instructions,
            onValueChange = onInstructions,
            label = { Text(stringResource(Res.string.engine_instructions_label)) },
            minLines = MIN_LINES,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("instructions_field"),
        )
        if (state.saveFailed) {
            Text(
                text = stringResource(Res.string.engine_instructions_save_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Annuler and Enregistrer, kept above the keyboard so a long text never hides them. */
@Composable
private fun InstructionsActions(
    canSave: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.engine_instructions_cancel)) }
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.testTag("instructions_save"),
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                }
                Text(stringResource(Res.string.engine_instructions_save))
            }
        }
    }
}

private const val MIN_LINES = 8
