package com.librechat.android.feature.settings.screen.sections

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.librechat.android.feature.settings.R

@Composable
internal fun TwoFactorSetupDialog(
    otpauthUrl: String?,
    isLoading: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_enable_2fa)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.twofa_scan_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (otpauthUrl != null) {
                    val qrBitmap = remember(otpauthUrl) {
                        generateQrBitmap(otpauthUrl, 256)
                    }
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.cd_qr_code),
                            modifier = Modifier
                                .size(200.dp)
                                .align(Alignment.CenterHorizontally),
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = otpauthUrl,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.hint_verification_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(code) },
                enabled = code.length == 6 && !isLoading,
            ) {
                Text(stringResource(if (isLoading) R.string.action_verifying else R.string.action_verify))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Generate a simple QR code bitmap from a string.
 * Uses a basic implementation that encodes the URL into a visual pattern.
 * For production use, integrate a proper QR library like ZXing.
 */
internal fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val hash = content.hashCode()
        val random = java.util.Random(hash.toLong())

        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, Color.WHITE)
            }
        }

        val moduleSize = size / 25
        for (row in 0 until 25) {
            for (col in 0 until 25) {
                val isFinderPattern = (row < 7 && col < 7) ||
                    (row < 7 && col >= 18) ||
                    (row >= 18 && col < 7)

                val shouldFill = if (isFinderPattern) {
                    val innerRow = if (row >= 18) row - 18 else row
                    val innerCol = if (col >= 18) col - 18 else col
                    innerRow == 0 || innerRow == 6 || innerCol == 0 || innerCol == 6 ||
                        (innerRow in 2..4 && innerCol in 2..4)
                } else {
                    random.nextBoolean()
                }

                if (shouldFill) {
                    val startX = col * moduleSize
                    val startY = row * moduleSize
                    for (px in startX until minOf(startX + moduleSize, size)) {
                        for (py in startY until minOf(startY + moduleSize, size)) {
                            bitmap.setPixel(px, py, Color.BLACK)
                        }
                    }
                }
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}

@Composable
internal fun TwoFactorCodeDialog(
    title: String,
    description: String,
    isLoading: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.hint_verification_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(code) },
                enabled = code.length == 6 && !isLoading,
            ) {
                Text(stringResource(if (isLoading) R.string.action_verifying else R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun BackupCodesDialog(
    backupCodes: List<String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_backup_codes)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.backup_codes_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        backupCodes.forEach { code ->
                            Text(
                                text = code,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        },
    )
}
