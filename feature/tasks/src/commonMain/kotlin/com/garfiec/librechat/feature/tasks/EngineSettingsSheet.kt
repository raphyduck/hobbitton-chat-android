package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.tasks.components.TasksBottomSheet
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_cancel
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_base_url
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_base_url_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_forget
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_invalid_url
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_issuer_url
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_issuer_url_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_save
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_scheduler_url
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_scheduler_url_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Where the engine, the portal and the scheduler are.
 *
 * **Nothing here is guessed from the chat's server URL.** `chat.hobbitton.at` → `agent.hobbitton.at`
 * is true of one deployment and silently false of the next, and the day it is false the portal's
 * bearer goes to whatever host the transformation lands on. Three fields cost a screen; a guess
 * costs a session (D-034).
 *
 * No credential is typed here any more (D-076): the portal is the only way in, and the edge
 * presents the engine's own password.
 */
@Composable
fun EngineSettingsSheet(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EngineSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSave by rememberUpdatedState(onSave)

    // The view model outlives the sheet, so re-read the stored values on each opening rather than
    // showing whatever was half-typed and abandoned last time.
    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.saved) { if (state.saved) currentOnSave() }

    TasksBottomSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        horizontalPadding = 24.dp,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.tasks_settings_title), style = MaterialTheme.typography.titleLarge)

        UrlField(
            value = state.baseUrl,
            onValueChange = viewModel::onBaseUrl,
            label = stringResource(Res.string.tasks_settings_base_url),
            hint = stringResource(Res.string.tasks_settings_base_url_hint),
            invalid = EngineSettingsField.BASE_URL in state.invalid,
            invalidMessage = stringResource(Res.string.tasks_settings_invalid_url),
        )

        UrlField(
            value = state.issuerUrl,
            onValueChange = viewModel::onIssuerUrl,
            label = stringResource(Res.string.tasks_settings_issuer_url),
            hint = stringResource(Res.string.tasks_settings_issuer_url_hint),
            invalid = EngineSettingsField.ISSUER_URL in state.invalid,
            invalidMessage = stringResource(Res.string.tasks_settings_invalid_url),
        )

        // Optional, and last of the three addresses: someone who has no scheduler must not
        // meet a required-looking field before the two that actually gate the tab.
        UrlField(
            value = state.schedulerUrl,
            onValueChange = viewModel::onSchedulerUrl,
            label = stringResource(Res.string.tasks_settings_scheduler_url),
            hint = stringResource(Res.string.tasks_settings_scheduler_url_hint),
            invalid = EngineSettingsField.SCHEDULER_URL in state.invalid,
            invalidMessage = stringResource(Res.string.tasks_settings_invalid_url),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = viewModel::forget,
                enabled = state.baseUrl.isNotBlank() || state.issuerUrl.isNotBlank() || state.schedulerUrl.isNotBlank(),
            ) {
                Text(
                    stringResource(Res.string.tasks_settings_forget),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.tasks_cancel)) }
                TextButton(onClick = viewModel::save) {
                    Text(stringResource(Res.string.tasks_settings_save))
                }
            }
        }
    }
}

@Composable
private fun UrlField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    invalid: Boolean,
    invalidMessage: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = invalid,
        supportingText = { Text(if (invalid) invalidMessage else hint) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
}
