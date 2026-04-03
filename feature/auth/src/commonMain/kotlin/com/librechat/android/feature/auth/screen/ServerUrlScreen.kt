package com.librechat.android.feature.auth.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import librechat_android.feature.auth.generated.resources.Res
import librechat_android.feature.auth.generated.resources.*
import com.librechat.android.feature.auth.viewmodel.ServerUrlViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ServerUrlScreen(
    onServerValidated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerUrlViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isValidated) {
        if (uiState.isValidated) {
            onServerValidated()
        }
    }

    if (uiState.showHttpWarning) {
        HttpWarningDialog(
            onConfirm = viewModel::confirmHttpConnection,
            onDismiss = viewModel::dismissHttpWarning,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.connect_to_librechat),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.enter_server_url_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = uiState.url,
            onValueChange = viewModel::onUrlChanged,
            label = { Text(stringResource(Res.string.server_url_label)) },
            placeholder = { Text(stringResource(Res.string.server_url_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = uiState.error != null,
            supportingText = uiState.error?.let { error ->
                { Text(text = error, color = MaterialTheme.colorScheme.error) }
            },
            enabled = !uiState.isLoading,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = viewModel::validateAndConnect,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading && uiState.url.isNotBlank(),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(Res.string.server_url_connect))
            }
        }
    }
}

@Composable
private fun HttpWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.insecure_connection_title)) },
        text = {
            Text(stringResource(Res.string.insecure_connection_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(Res.string.connect_anyway),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
