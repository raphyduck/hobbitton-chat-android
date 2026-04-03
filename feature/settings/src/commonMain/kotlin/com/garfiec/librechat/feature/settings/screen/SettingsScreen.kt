package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.OtpVerificationDialog
import librechat_mobile.feature.settings.generated.resources.Res
import librechat_mobile.feature.settings.generated.resources.*
import com.garfiec.librechat.feature.settings.screen.sections.AboutInfo
import com.garfiec.librechat.feature.settings.screen.sections.AccountInfo
import com.garfiec.librechat.feature.settings.screen.sections.BackupCodesDialog
import com.garfiec.librechat.feature.settings.screen.sections.DangerZone
import com.garfiec.librechat.feature.settings.screen.sections.DataExtraActions
import com.garfiec.librechat.feature.settings.screen.sections.SettingsRow
import com.garfiec.librechat.feature.settings.screen.sections.SpeechDetailButtons
import com.garfiec.librechat.feature.settings.screen.sections.TabletSidebarGestureToggle
import com.garfiec.librechat.feature.settings.screen.sections.ThemeSelector
import com.garfiec.librechat.feature.settings.screen.sections.TwoFactorCodeDialog
import com.garfiec.librechat.feature.settings.screen.sections.TwoFactorSetupDialog
import com.garfiec.librechat.feature.settings.viewmodel.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToArchived: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSharedLinks: () -> Unit = {},
    onNavigateToApiKeys: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showRevokeKeysDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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

    LaunchedEffect(uiState.showExportComingSoon) {
        if (uiState.showExportComingSoon) {
            snackbarHostState.showSnackbar(message = "Export is coming soon")
            viewModel.dismissExportComingSoon()
        }
    }

    LaunchedEffect(uiState.mcpReinitializeMessage) {
        val message = uiState.mcpReinitializeMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissMcpReinitializeMessage()
    }

    LaunchedEffect(uiState.isLoggedOut, uiState.isAccountDeleted) {
        if (uiState.isLoggedOut || uiState.isAccountDeleted) {
            onLogout()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading && uiState.user == null) {
            LoadingIndicator()
        } else if (uiState.error != null && uiState.user == null) {
            ErrorBanner(
                message = uiState.error ?: stringResource(Res.string.error_could_not_load_settings),
                modifier = Modifier.padding(innerPadding),
                onRetry = { viewModel.retry() },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // Account section
                item(key = "account_header") {
                    SectionHeader(stringResource(Res.string.section_account))
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
                    SectionHeader(stringResource(Res.string.section_balance))
                }
                item(key = "balance_section") {
                    BalanceSection(
                        tokenCredits = uiState.tokenCredits,
                        isLoading = uiState.isBalanceLoading,
                    )
                }

                // Appearance section
                item(key = "appearance_header") {
                    SectionHeader(stringResource(Res.string.section_appearance))
                }
                item(key = "theme_selector") {
                    ThemeSelector(
                        selected = uiState.themeMode,
                        onSelect = viewModel::setThemeMode,
                    )
                }

                // Tablet section
                item(key = "tablet_header") {
                    SectionHeader(stringResource(Res.string.section_tablet))
                }
                item(key = "tablet_sidebar_gesture") {
                    TabletSidebarGestureToggle(
                        gestureEnabled = uiState.tabletSidebarGestureEnabled,
                        onGestureEnabledChange = viewModel::setTabletSidebarGestureEnabled,
                    )
                }

                // Chat Preferences section
                item(key = "chat_header") {
                    SectionHeader(stringResource(Res.string.section_chat_preferences))
                }
                item(key = "chat_settings") {
                    ChatSettingsSection(
                        fontSize = uiState.chatFontSize,
                        autoScrollEnabled = uiState.autoScrollEnabled,
                        showThinkingBlocks = uiState.showThinkingBlocks,
                        showImageDescriptions = uiState.showImageDescriptions,
                        dismissKeyboardOnSend = uiState.dismissKeyboardOnSend,
                        chatLayoutStyle = uiState.chatLayoutStyle,
                        showAvatars = uiState.showAvatars,
                        showBubbles = uiState.showBubbles,
                        latexRenderer = uiState.latexRenderer,
                        onFontSizeChange = viewModel::setChatFontSize,
                        onAutoScrollChange = viewModel::setAutoScrollEnabled,
                        onShowThinkingChange = viewModel::setShowThinkingBlocks,
                        onShowImageDescriptionsChange = viewModel::setShowImageDescriptions,
                        onDismissKeyboardOnSendChange = viewModel::setDismissKeyboardOnSend,
                        onChatLayoutStyleChange = viewModel::setChatLayoutStyle,
                        onShowAvatarsChange = viewModel::setShowAvatars,
                        onShowBubblesChange = viewModel::setShowBubbles,
                        onLatexRendererChange = viewModel::setLatexRenderer,
                    )
                }

                // General section
                item(key = "general_header") {
                    SectionHeader(stringResource(Res.string.section_general))
                }
                item(key = "language_row") {
                    SettingsRow(
                        icon = Icons.Default.Language,
                        title = stringResource(Res.string.language),
                        subtitle = uiState.selectedLanguage.uppercase(),
                        onClick = viewModel::showLanguageDialog,
                    )
                }
                item(key = "personalization_row") {
                    SettingsRow(
                        icon = Icons.Default.Person,
                        title = stringResource(Res.string.personalization),
                        subtitle = if (uiState.personalizationEnabled) {
                            stringResource(Res.string.status_enabled)
                        } else {
                            stringResource(Res.string.status_disabled)
                        },
                        onClick = viewModel::showPersonalizationDialog,
                    )
                }

                // Advanced section
                item(key = "advanced_header") {
                    SectionHeader(stringResource(Res.string.section_advanced))
                }
                item(key = "fork_settings_row") {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        title = stringResource(Res.string.fork_behavior),
                        subtitle = ForkMode.fromApiValue(uiState.forkMode).label,
                        onClick = viewModel::showForkSettingsDialog,
                    )
                }
                item(key = "commands_row") {
                    SettingsRow(
                        icon = Icons.Default.Terminal,
                        title = stringResource(Res.string.commands),
                        subtitle = stringResource(Res.string.commands_enabled_count, uiState.commands.count { it.enabled }),
                        onClick = viewModel::showCommandsScreen,
                    )
                }

                // Speech section
                item(key = "speech_header") {
                    SectionHeader(stringResource(Res.string.section_speech))
                }
                item(key = "speech_settings") {
                    SpeechSettingsSection(
                        autoSendAfterSttEnabled = uiState.sttAutoSend,
                        autoReadEnabled = uiState.autoReadEnabled,
                        selectedVoice = uiState.selectedVoice,
                        availableVoices = uiState.availableVoices,
                        ttsSource = uiState.ttsSource,
                        onAutoSendAfterSttChange = viewModel::setAutoSendAfterStt,
                        onAutoReadChange = viewModel::setAutoReadEnabled,
                        onVoiceSelected = viewModel::selectVoice,
                        onTestVoice = viewModel::testVoice,
                    )
                }
                item(key = "speech_detail_buttons") {
                    SpeechDetailButtons(
                        onSttDetailClick = viewModel::showSttDetailDialog,
                        onTtsDetailClick = viewModel::showTtsDetailDialog,
                    )
                }

                // Memories section
                item(key = "memories_header") {
                    SectionHeader(stringResource(Res.string.section_memories))
                }
                item(key = "memories_settings") {
                    MemoriesSettingsSection(
                        memories = uiState.memories,
                        memoriesEnabled = uiState.memoriesEnabled,
                        showMemoryDialog = uiState.showMemoryDialog,
                        editingMemory = uiState.editingMemory,
                        onToggleEnabled = viewModel::toggleMemoriesEnabled,
                        onAddMemory = viewModel::showAddMemoryDialog,
                        onEditMemory = viewModel::showEditMemoryDialog,
                        onDeleteMemory = viewModel::deleteMemory,
                        onDismissDialog = viewModel::dismissMemoryDialog,
                        onSaveMemory = viewModel::saveMemory,
                    )
                }

                // Data Management section
                item(key = "data_header") {
                    SectionHeader(stringResource(Res.string.section_data_management))
                }
                item(key = "data_settings") {
                    DataSettingsSection(
                        archivedCount = uiState.archivedCount,
                        isClearing = uiState.isClearing,
                        onClearAllChats = viewModel::clearAllChats,
                        onViewArchived = onNavigateToArchived,
                        onExportAllData = viewModel::exportAllData,
                    )
                }
                item(key = "data_extra_actions") {
                    DataExtraActions(
                        onSharedLinksClick = onNavigateToSharedLinks,
                        onClearCacheClick = { showClearCacheDialog = true },
                        isCacheClearing = uiState.isCacheClearing,
                        onRevokeKeysClick = { showRevokeKeysDialog = true },
                        isKeyRevoking = uiState.isKeyRevoking,
                    )
                }

                // MCP section
                item(key = "mcp_header") {
                    SectionHeader(stringResource(Res.string.section_mcp_servers))
                }
                item(key = "mcp_settings") {
                    McpSettingsSection(
                        servers = uiState.mcpServers,
                        connectionStatus = uiState.mcpConnectionStatus,
                        reinitializingServers = uiState.mcpReinitializingServers,
                        error = uiState.mcpError,
                        onAddServer = viewModel::showAddMcpServerDialog,
                        onEditServer = viewModel::showEditMcpServerDialog,
                        onDeleteServer = viewModel::deleteMcpServer,
                        onReinitialize = viewModel::reinitializeMcpServer,
                    )
                }

                // Security section
                item(key = "security_header") {
                    SectionHeader(stringResource(Res.string.section_security))
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
                    SettingsRow(
                        icon = Icons.Default.Key,
                        title = stringResource(Res.string.api_keys),
                        subtitle = stringResource(Res.string.api_keys_subtitle),
                        onClick = onNavigateToApiKeys,
                    )
                }

                // About section
                item(key = "about_header") {
                    SectionHeader(stringResource(Res.string.section_about))
                }
                item(key = "about_info") {
                    AboutInfo(serverUrl = uiState.serverUrl)
                }

                // Danger zone
                item(key = "danger_header") {
                    SectionHeader(stringResource(Res.string.section_danger_zone))
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
    }

    // MCP server add/edit dialog
    if (uiState.showMcpServerDialog) {
        McpServerDialog(
            editingServer = uiState.editingMcpServer,
            onDismiss = viewModel::dismissMcpServerDialog,
            onSave = { name, description, url, type, apiKey, oauth ->
                viewModel.saveMcpServer(name, description, url, type, apiKey, oauth)
            },
        )
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

    // STT detail dialog
    if (uiState.showSttDetailDialog) {
        SttDetailDialog(
            selectedEngine = uiState.sttEngine,
            selectedLanguage = uiState.sttLanguage,
            availableEngines = listOf("Default", "Whisper", "Google"),
            availableLanguages = listOf("Auto-detect", "English", "Spanish", "French", "German", "Japanese", "Chinese"),
            onConfirm = viewModel::saveSttSettings,
            onDismiss = viewModel::dismissSttDetailDialog,
        )
    }

    // TTS detail dialog
    if (uiState.showTtsDetailDialog) {
        TtsDetailDialog(
            selectedEngine = uiState.ttsEngine,
            selectedVoice = uiState.ttsVoice,
            speechRate = uiState.ttsSpeechRate,
            pitch = uiState.ttsPitch,
            deviceVoiceName = uiState.ttsDeviceVoiceName,
            cachingEnabled = uiState.ttsCaching,
            ttsSource = uiState.ttsSource,
            availableEngines = listOf("Default", "ElevenLabs", "OpenAI"),
            availableVoices = uiState.availableVoices.map { it.name }.ifEmpty {
                listOf("Default", "Alloy", "Echo", "Fable", "Onyx", "Nova", "Shimmer")
            },
            availableDeviceVoices = uiState.availableDeviceVoices,
            isPreviewPlaying = uiState.isTtsPreviewPlaying,
            onPreviewDevice = viewModel::previewDeviceTts,
            onPreviewServer = viewModel::previewServerTts,
            onStopPreview = viewModel::stopTtsPreview,
            onConfirm = viewModel::saveTtsSettings,
            onDismiss = viewModel::dismissTtsDetailDialog,
        )
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(Res.string.dialog_title_sign_out)) },
            text = { Text(stringResource(Res.string.dialog_sign_out_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    },
                ) {
                    Text(stringResource(Res.string.action_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    // 2FA enable setup dialog
    if (uiState.showTwoFactorSetupDialog) {
        TwoFactorSetupDialog(
            otpauthUrl = uiState.twoFactorOtpauthUrl,
            isLoading = uiState.isTwoFactorLoading,
            onConfirm = viewModel::confirmEnableTwoFactor,
            onDismiss = viewModel::dismissTwoFactorSetupDialog,
        )
    }

    // 2FA disable dialog
    if (uiState.showDisableTwoFactorDialog) {
        TwoFactorCodeDialog(
            title = stringResource(Res.string.dialog_title_disable_2fa),
            description = stringResource(Res.string.twofa_disable_instructions),
            isLoading = uiState.isTwoFactorLoading,
            onConfirm = viewModel::confirmDisableTwoFactor,
            onDismiss = viewModel::dismissDisableTwoFactorDialog,
        )
    }

    // Backup codes dialog
    if (uiState.showBackupCodesDialog) {
        BackupCodesDialog(
            backupCodes = uiState.backupCodes,
            onDismiss = viewModel::dismissBackupCodesDialog,
        )
    }

    // Clear cache confirmation
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(Res.string.dialog_title_clear_cache)) },
            text = { Text(stringResource(Res.string.dialog_clear_cache_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearCache()
                    },
                ) {
                    Text(stringResource(Res.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    // Revoke keys confirmation
    if (showRevokeKeysDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeKeysDialog = false },
            title = { Text(stringResource(Res.string.dialog_title_revoke_keys)) },
            text = { Text(stringResource(Res.string.dialog_revoke_keys_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevokeKeysDialog = false
                        viewModel.revokeAllKeys()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(Res.string.action_revoke_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeKeysDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    // Delete account confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(Res.string.dialog_title_delete_account)) },
            text = {
                Text(stringResource(Res.string.dialog_delete_account_message))
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
                    Text(stringResource(Res.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    // OTP dialog for account deletion when 2FA is enabled
    if (uiState.showDeleteAccountOtpDialog) {
        OtpVerificationDialog(
            title = stringResource(Res.string.otp_title_verify_identity),
            description = stringResource(Res.string.otp_desc_delete_account),
            isLoading = uiState.isLoading,
            onVerify = { token, backupCode ->
                viewModel.deleteAccount(token = token, backupCode = backupCode)
            },
            onDismiss = viewModel::dismissDeleteAccountOtpDialog,
            verifyLabel = stringResource(Res.string.otp_verify),
            cancelLabel = stringResource(Res.string.otp_cancel),
            backupCodeLabel = stringResource(Res.string.otp_backup_code_label),
            useBackupToggleLabel = stringResource(Res.string.otp_use_backup_code),
            useOtpToggleLabel = stringResource(Res.string.otp_use_otp_code),
        )
    }

    // OTP dialog for enabling 2FA when re-enrolling
    if (uiState.showEnableTwoFactorOtpDialog) {
        OtpVerificationDialog(
            title = stringResource(Res.string.otp_title_verify_identity),
            description = stringResource(Res.string.otp_desc_reenroll_2fa),
            isLoading = uiState.isTwoFactorLoading,
            onVerify = { token, backupCode ->
                viewModel.enableTwoFactorWithOtp(token = token, backupCode = backupCode)
            },
            onDismiss = viewModel::dismissEnableTwoFactorOtpDialog,
            verifyLabel = stringResource(Res.string.otp_verify),
            cancelLabel = stringResource(Res.string.otp_cancel),
            backupCodeLabel = stringResource(Res.string.otp_backup_code_label),
            useBackupToggleLabel = stringResource(Res.string.otp_use_backup_code),
            useOtpToggleLabel = stringResource(Res.string.otp_use_otp_code),
        )
    }

    // OTP dialog for regenerating backup codes
    if (uiState.showBackupCodesOtpDialog) {
        OtpVerificationDialog(
            title = stringResource(Res.string.otp_title_verify_identity),
            description = stringResource(Res.string.otp_desc_regenerate_backup_codes),
            isLoading = uiState.isTwoFactorLoading,
            onVerify = { token, backupCode ->
                viewModel.viewBackupCodesWithOtp(token = token, backupCode = backupCode)
            },
            onDismiss = viewModel::dismissBackupCodesOtpDialog,
            verifyLabel = stringResource(Res.string.otp_verify),
            cancelLabel = stringResource(Res.string.otp_cancel),
            backupCodeLabel = stringResource(Res.string.otp_backup_code_label),
            useBackupToggleLabel = stringResource(Res.string.otp_use_backup_code),
            useOtpToggleLabel = stringResource(Res.string.otp_use_otp_code),
        )
    }

    // Language selector dialog
    if (uiState.showLanguageDialog) {
        LanguageSelectorDialog(
            selectedLanguage = uiState.selectedLanguage,
            onLanguageSelected = viewModel::setLanguage,
            onDismiss = viewModel::dismissLanguageDialog,
        )
    }

    // Fork settings dialog
    if (uiState.showForkSettingsDialog) {
        ForkSettingsDialog(
            selectedMode = ForkMode.fromApiValue(uiState.forkMode),
            onModeSelected = { mode ->
                viewModel.setForkMode(mode.apiValue)
            },
            onDismiss = viewModel::dismissForkSettingsDialog,
        )
    }

    // Personalization dialog
    if (uiState.showPersonalizationDialog) {
        PersonalizationDialog(
            aboutUser = uiState.aboutUser,
            responseStyle = uiState.responseStyle,
            enabled = uiState.personalizationEnabled,
            onSave = viewModel::savePersonalization,
            onDismiss = viewModel::dismissPersonalizationDialog,
        )
    }

    // Commands screen (full screen overlay)
    if (uiState.showCommandsScreen) {
        CommandsConfigScreen(
            commands = uiState.commands.map { cmd ->
                CommandConfig(
                    name = cmd.name,
                    description = cmd.description,
                    enabled = cmd.enabled,
                )
            },
            onToggleCommand = viewModel::toggleCommand,
            onNavigateBack = viewModel::hideCommandsScreen,
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
