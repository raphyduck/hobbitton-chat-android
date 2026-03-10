package com.librechat.android.feature.settings.screen

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.librechat.android.feature.settings.R
import com.librechat.android.core.ui.components.ErrorBanner
import com.librechat.android.core.ui.components.LoadingIndicator
import com.librechat.android.feature.settings.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    onLogout: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToApiKeys: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_account)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        AccountSettingsContent(
            onLogout = onLogout,
            onNavigateToApiKeys = onNavigateToApiKeys,
            snackbarHostState = snackbarHostState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            viewModel = viewModel,
        )
    }
}

/**
 * Reusable Account settings content (without Scaffold/TopAppBar).
 * Used by both the standalone screen and the tabbed settings screen.
 */
@Composable
fun AccountSettingsContent(
    onLogout: () -> Unit,
    onNavigateToApiKeys: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = error,
            actionLabel = "Retry",
        )
        viewModel.dismissError()
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.retry()
        }
    }

    LaunchedEffect(uiState.isLoggedOut, uiState.isAccountDeleted) {
        if (uiState.isLoggedOut || uiState.isAccountDeleted) {
            onLogout()
        }
    }

    if (uiState.isLoading && uiState.user == null) {
        LoadingIndicator()
    } else if (uiState.error != null && uiState.user == null) {
        ErrorBanner(
            message = uiState.error ?: stringResource(R.string.error_could_not_load_settings),
            modifier = modifier,
            onRetry = { viewModel.retry() },
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
        ) {
            // Account section
            item(key = "account_header") {
                SectionHeader(stringResource(R.string.section_profile))
            }
            item(key = "account_info") {
                AccountInfo(
                    name = uiState.user?.name ?: "",
                    email = uiState.user?.email ?: "",
                    avatarUrl = uiState.user?.avatar,
                    onAvatarClick = viewModel::showAvatarDialog,
                )
            }

            // Balance section
            item(key = "balance_header") {
                SectionHeader(stringResource(R.string.section_balance))
            }
            item(key = "balance_section") {
                BalanceSection(
                    tokenCredits = uiState.tokenCredits,
                    isLoading = uiState.isBalanceLoading,
                )
            }

            // Security section
            item(key = "security_header") {
                SectionHeader(stringResource(R.string.section_security))
            }
            item(key = "security_settings") {
                SecuritySection(
                    isTwoFactorEnabled = uiState.isTwoFactorEnabled,
                    isLoading = uiState.isTwoFactorLoading,
                    onToggleTwoFactor = viewModel::toggleTwoFactor,
                    onViewBackupCodes = viewModel::viewBackupCodes,
                )
            }
            item(key = "api_keys_row") {
                AccountSettingsRow(
                    icon = Icons.Default.Key,
                    title = stringResource(R.string.api_keys),
                    subtitle = stringResource(R.string.api_keys_subtitle),
                    onClick = onNavigateToApiKeys,
                )
            }

            // Danger zone
            item(key = "danger_header") {
                SectionHeader(stringResource(R.string.section_danger_zone))
            }
            item(key = "danger_actions") {
                DangerZone(
                    isLoading = uiState.isLoading,
                    onLogoutClick = { showLogoutDialog = true },
                    onDeleteClick = { showDeleteDialog = true },
                )
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // Avatar upload dialog
    if (uiState.showAvatarDialog) {
        AvatarUploadDialog(
            currentAvatarUrl = uiState.user?.avatar,
            isUploading = uiState.isAvatarUploading,
            onPickImage = viewModel::uploadAvatar,
            onDismiss = viewModel::dismissAvatarDialog,
        )
    }

    // 2FA enable setup dialog
    if (uiState.showTwoFactorSetupDialog) {
        AccountTwoFactorSetupDialog(
            otpauthUrl = uiState.twoFactorOtpauthUrl,
            isLoading = uiState.isTwoFactorLoading,
            onConfirm = viewModel::confirmEnableTwoFactor,
            onDismiss = viewModel::dismissTwoFactorSetupDialog,
        )
    }

    // 2FA disable dialog
    if (uiState.showDisableTwoFactorDialog) {
        AccountTwoFactorCodeDialog(
            title = stringResource(R.string.dialog_title_disable_2fa),
            description = stringResource(R.string.twofa_disable_instructions),
            isLoading = uiState.isTwoFactorLoading,
            onConfirm = viewModel::confirmDisableTwoFactor,
            onDismiss = viewModel::dismissDisableTwoFactorDialog,
        )
    }

    // Backup codes dialog
    if (uiState.showBackupCodesDialog) {
        AccountBackupCodesDialog(
            backupCodes = uiState.backupCodes,
            onDismiss = viewModel::dismissBackupCodesDialog,
        )
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.dialog_title_sign_out)) },
            text = { Text(stringResource(R.string.dialog_sign_out_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    },
                ) {
                    Text(stringResource(R.string.action_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Delete account confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_title_delete_account)) },
            text = {
                Text(stringResource(R.string.dialog_delete_account_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteAccount()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { heading() },
    )
}

@Composable
private fun AccountInfo(
    name: String,
    email: String,
    avatarUrl: String? = null,
    onAvatarClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer,
            onClick = onAvatarClick,
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = stringResource(R.string.cd_user_avatar),
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (name.isNotBlank()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (email.isNotBlank()) {
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onAvatarClick) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = stringResource(R.string.cd_change_avatar),
                modifier = Modifier.size(20.dp),
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun DangerZone(
    isLoading: Boolean,
    onLogoutClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onLogoutClick,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_sign_out))
        }
        Button(
            onClick = onDeleteClick,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(stringResource(R.string.delete_account))
        }
    }
}

@Composable
private fun AccountSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun AccountTwoFactorSetupDialog(
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
                    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(otpauthUrl) {
                        qrBitmap = withContext(Dispatchers.Default) {
                            generateQrBitmapForAccount(otpauthUrl, 256)
                        }
                    }
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = stringResource(R.string.cd_qr_code),
                            modifier = Modifier
                                .size(200.dp)
                                .align(Alignment.CenterHorizontally),
                        )
                    } else {
                        CircularProgressIndicator(
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
                OutlinedTextField(
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

private fun generateQrBitmapForAccount(content: String, size: Int): Bitmap? {
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
private fun AccountTwoFactorCodeDialog(
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
                OutlinedTextField(
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
private fun AccountBackupCodesDialog(
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
