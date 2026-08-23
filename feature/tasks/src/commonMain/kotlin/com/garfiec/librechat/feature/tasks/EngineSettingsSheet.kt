package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_cancel
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_advanced
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_base_url
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_base_url_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_client_id
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_forget
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_invalid_url
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_issuer_url
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_issuer_url_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_scheduler_url
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_scheduler_url_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_password
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_password_kept
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_password_required
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_reveal
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_save
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_title
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_username
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_username_required
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Where the engine is, and what the app shows to get in.
 *
 * **Nothing here is guessed from the chat's server URL.** `chat.hobbitton.at` → `agent.hobbitton.at`
 * is true of one deployment and silently false of the next, and the day it is false the engine's
 * password goes to whatever host the transformation lands on. Four fields cost a screen; a guess
 * costs a secret (D-034).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineSettingsSheet(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    viewModel: EngineSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSave by rememberUpdatedState(onSave)
    var revealed by remember { mutableStateOf(false) }

    // The view model outlives the sheet, so re-read the stored values on each opening rather than
    // showing whatever was half-typed and abandoned last time.
    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.saved) { if (state.saved) currentOnSave() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
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
            // meet a required-looking field before the credentials that actually gate the tab.
            UrlField(
                value = state.schedulerUrl,
                onValueChange = viewModel::onSchedulerUrl,
                label = stringResource(Res.string.tasks_settings_scheduler_url),
                hint = stringResource(Res.string.tasks_settings_scheduler_url_hint),
                invalid = EngineSettingsField.SCHEDULER_URL in state.invalid,
                invalidMessage = stringResource(Res.string.tasks_settings_invalid_url),
            )

            val usernameInvalid = EngineSettingsField.USERNAME in state.invalid
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsername,
                label = { Text(stringResource(Res.string.tasks_settings_username)) },
                isError = usernameInvalid,
                supportingText = if (usernameInvalid) {
                    { Text(stringResource(Res.string.tasks_settings_username_required)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val passwordInvalid = EngineSettingsField.PASSWORD in state.invalid
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPassword,
                label = { Text(stringResource(Res.string.tasks_settings_password)) },
                // Masked by default: this ends up in screenshots and accessibility dumps otherwise,
                // exactly as the MCP server headers and provider keys are already treated.
                visualTransformation = if (revealed) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(Res.string.tasks_settings_reveal),
                        )
                    }
                },
                isError = passwordInvalid,
                supportingText = {
                    Text(
                        when {
                            passwordInvalid -> stringResource(Res.string.tasks_settings_password_required)
                            // An empty field over a stored password is not an empty password. Saying
                            // so is what stops someone retyping a secret they never had to.
                            state.passwordStored -> stringResource(Res.string.tasks_settings_password_kept)
                            else -> ""
                        },
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                stringResource(Res.string.tasks_settings_advanced),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.clientId,
                onValueChange = viewModel::onClientId,
                label = { Text(stringResource(Res.string.tasks_settings_client_id)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = viewModel::forget,
                    enabled = state.passwordStored || state.baseUrl.isNotBlank(),
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
