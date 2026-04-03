package com.librechat.android.feature.settings.screen.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import librechat_android.feature.settings.generated.resources.Res
import librechat_android.feature.settings.generated.resources.*

@Composable
internal fun DataExtraActions(
    onSharedLinksClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    isCacheClearing: Boolean,
    onRevokeKeysClick: () -> Unit,
    isKeyRevoking: Boolean,
) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Shared Links
            OutlinedButton(
                onClick = onSharedLinksClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.shared_links))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Clear cache
            OutlinedButton(
                onClick = onClearCacheClick,
                enabled = !isCacheClearing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (isCacheClearing) Res.string.clearing else Res.string.clear_cache))
            }

            // Revoke API keys
            OutlinedButton(
                onClick = onRevokeKeysClick,
                enabled = !isKeyRevoking,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(if (isKeyRevoking) Res.string.revoking else Res.string.revoke_all_api_keys))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
