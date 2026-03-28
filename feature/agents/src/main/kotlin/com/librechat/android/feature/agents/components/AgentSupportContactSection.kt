package com.librechat.android.feature.agents.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.librechat.android.feature.agents.R

@androidx.compose.runtime.Immutable
data class SupportContactState(
    val name: String = "",
    val email: String = "",
)

@Composable
fun AgentSupportContactSection(
    supportContact: SupportContactState,
    onSupportContactChanged: (SupportContactState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.label_support_contact),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Name field
        Text(
            text = stringResource(R.string.label_name),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = supportContact.name,
            onValueChange = {
                onSupportContactChanged(supportContact.copy(name = it))
            },
            placeholder = { Text(stringResource(R.string.support_contact_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Email field
        Text(
            text = stringResource(R.string.label_email),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = supportContact.email,
            onValueChange = {
                onSupportContactChanged(supportContact.copy(email = it))
            },
            placeholder = { Text("support@example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
