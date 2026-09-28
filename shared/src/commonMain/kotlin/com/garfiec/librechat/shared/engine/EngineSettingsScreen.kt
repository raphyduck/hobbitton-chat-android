package com.garfiec.librechat.shared.engine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.network.engine.EngineAccess
import com.garfiec.librechat.shared.resources.Res
import com.garfiec.librechat.shared.resources.engine_settings_back
import com.garfiec.librechat.shared.resources.engine_settings_cancel
import com.garfiec.librechat.shared.resources.engine_settings_engine
import com.garfiec.librechat.shared.resources.engine_settings_instructions
import com.garfiec.librechat.shared.resources.engine_settings_instructions_hint
import com.garfiec.librechat.shared.resources.engine_settings_platform
import com.garfiec.librechat.shared.resources.engine_settings_platform_hint
import com.garfiec.librechat.shared.resources.engine_settings_portal
import com.garfiec.librechat.shared.resources.engine_settings_scheduler
import com.garfiec.librechat.shared.resources.engine_settings_sign_out
import com.garfiec.librechat.shared.resources.engine_settings_sign_out_confirm
import com.garfiec.librechat.shared.resources.engine_settings_sign_out_hint
import com.garfiec.librechat.shared.resources.engine_settings_theme
import com.garfiec.librechat.shared.resources.engine_settings_title
import com.garfiec.librechat.shared.resources.engine_settings_usage
import com.garfiec.librechat.shared.resources.engine_theme_dark
import com.garfiec.librechat.shared.resources.engine_theme_light
import com.garfiec.librechat.shared.resources.engine_theme_system
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What the settings screen shows. */
data class EngineSettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val addresses: EngineAccess? = null,
)

/**
 * The engine shell's settings (D-077): what is left once LibreChat's account, keys, presets,
 * memories, MCP servers and sharing have gone with it — the theme, the platform's addresses (read
 * only: changing one is signing out and in again), the instructions sent with every turn, the
 * week's usage, and signing out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EngineSettingsScreen(
    state: EngineSettingsUiState,
    onBack: () -> Unit,
    onOpenInstructions: () -> Unit,
    onOpenUsage: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.engine_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.engine_settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        SettingsBody(
            state = state,
            padding = padding,
            onOpenInstructions = onOpenInstructions,
            onOpenUsage = onOpenUsage,
            onThemeMode = onThemeMode,
            onSignOut = { confirmSignOut = true },
        )
        if (confirmSignOut) {
            SignOutDialog(
                onConfirm = {
                    confirmSignOut = false
                    onSignOut()
                },
                onDismiss = { confirmSignOut = false },
            )
        }
    }
}

/** Signing out forgets the portal session: asked once, never on a stray tap. */
@Composable
private fun SignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.engine_settings_sign_out)) },
        text = { Text(stringResource(Res.string.engine_settings_sign_out_hint)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("settings_sign_out_confirm")) {
                Text(stringResource(Res.string.engine_settings_sign_out_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.engine_settings_cancel)) }
        },
    )
}

@Composable
private fun SettingsBody(
    state: EngineSettingsUiState,
    padding: PaddingValues,
    onOpenInstructions: () -> Unit,
    onOpenUsage: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle(Res.string.engine_settings_theme)
        ThemeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = state.themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.themeMode == mode, onClick = null)
                Text(stringResource(mode.label()), modifier = Modifier.padding(start = 12.dp))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SectionTitle(Res.string.engine_settings_platform)
        AddressRow(Res.string.engine_settings_engine, state.addresses?.baseUrl)
        AddressRow(Res.string.engine_settings_scheduler, state.addresses?.schedulerUrl)
        AddressRow(Res.string.engine_settings_portal, state.addresses?.issuerUrl)
        Text(
            text = stringResource(Res.string.engine_settings_platform_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        ListItem(
            headlineContent = { Text(stringResource(Res.string.engine_settings_instructions)) },
            supportingContent = { Text(stringResource(Res.string.engine_settings_instructions_hint)) },
            leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
            modifier = Modifier
                .selectable(selected = false, onClick = onOpenInstructions, role = Role.Button)
                .testTag("settings_instructions"),
        )
        ListItem(
            headlineContent = { Text(stringResource(Res.string.engine_settings_usage)) },
            leadingContent = { Icon(Icons.Outlined.QueryStats, contentDescription = null) },
            modifier = Modifier.selectable(selected = false, onClick = onOpenUsage, role = Role.Button),
        )
        ListItem(
            headlineContent = {
                Text(stringResource(Res.string.engine_settings_sign_out), color = MaterialTheme.colorScheme.error)
            },
            leadingContent = {
                Icon(
                    Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            modifier = Modifier
                .selectable(selected = false, onClick = onSignOut, role = Role.Button)
                .testTag("settings_sign_out"),
        )
    }
}

@Composable
private fun SectionTitle(title: StringResource) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun AddressRow(label: StringResource, value: String?) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        supportingContent = { Text(value?.takeIf { it.isNotBlank() } ?: "—") },
    )
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> Res.string.engine_theme_system
    ThemeMode.LIGHT -> Res.string.engine_theme_light
    ThemeMode.DARK -> Res.string.engine_theme_dark
}
