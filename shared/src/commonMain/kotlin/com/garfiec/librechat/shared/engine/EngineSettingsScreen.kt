package com.garfiec.librechat.shared.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.network.engine.EngineAccess
import com.garfiec.librechat.core.ui.components.SectionDivider
import com.garfiec.librechat.core.ui.components.SectionGroup
import com.garfiec.librechat.core.ui.components.SectionLabel
import com.garfiec.librechat.shared.resources.Res
import com.garfiec.librechat.shared.resources.engine_settings_appearance
import com.garfiec.librechat.shared.resources.engine_settings_assistant
import com.garfiec.librechat.shared.resources.engine_settings_attention
import com.garfiec.librechat.shared.resources.engine_settings_attention_hint
import com.garfiec.librechat.shared.resources.engine_settings_back
import com.garfiec.librechat.shared.resources.engine_settings_budget
import com.garfiec.librechat.shared.resources.engine_settings_budget_hint
import com.garfiec.librechat.shared.resources.engine_settings_cancel
import com.garfiec.librechat.shared.resources.engine_settings_default_model
import com.garfiec.librechat.shared.resources.engine_settings_default_model_hint
import com.garfiec.librechat.shared.resources.engine_settings_default_model_server
import com.garfiec.librechat.shared.resources.engine_settings_default_model_server_named
import com.garfiec.librechat.shared.resources.engine_settings_default_model_unavailable
import com.garfiec.librechat.shared.resources.engine_settings_dictation
import com.garfiec.librechat.shared.resources.engine_settings_engine
import com.garfiec.librechat.shared.resources.engine_settings_instructions
import com.garfiec.librechat.shared.resources.engine_settings_instructions_hint
import com.garfiec.librechat.shared.resources.engine_settings_local_dictation
import com.garfiec.librechat.shared.resources.engine_settings_local_dictation_hint
import com.garfiec.librechat.shared.resources.engine_settings_notifications
import com.garfiec.librechat.shared.resources.engine_settings_platform
import com.garfiec.librechat.shared.resources.engine_settings_platform_hint
import com.garfiec.librechat.shared.resources.engine_settings_portal
import com.garfiec.librechat.shared.resources.engine_settings_scheduler
import com.garfiec.librechat.shared.resources.engine_settings_sign_out
import com.garfiec.librechat.shared.resources.engine_settings_sign_out_confirm
import com.garfiec.librechat.shared.resources.engine_settings_sign_out_hint
import com.garfiec.librechat.shared.resources.engine_settings_text_size
import com.garfiec.librechat.shared.resources.engine_settings_theme
import com.garfiec.librechat.shared.resources.engine_settings_title
import com.garfiec.librechat.shared.resources.engine_settings_usage
import com.garfiec.librechat.shared.resources.engine_text_size_large
import com.garfiec.librechat.shared.resources.engine_text_size_medium
import com.garfiec.librechat.shared.resources.engine_text_size_small
import com.garfiec.librechat.shared.resources.engine_theme_dark
import com.garfiec.librechat.shared.resources.engine_theme_light
import com.garfiec.librechat.shared.resources.engine_theme_system
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What the settings screen shows. */
data class EngineSettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val addresses: EngineAccess? = null,
    /** A question or a finished reply rings and notifies (03/10/2026). */
    val attentionSound: Boolean = true,
    /** The mic heard by the phone itself rather than the scheduler (19/10/2026). */
    val localDictation: Boolean = true,
    /** The conversation's text size (lot 4, 10/10/2026). */
    val chatFontSize: ChatFontSize = ChatFontSize.MEDIUM,
    /** Who is signed in, and where: the drawer's foot, at the head of the settings. */
    val account: DrawerAccount = DrawerAccount(),
    /** The model a new conversation starts on, by id; null follows the engine's (19/10/2026). */
    val defaultModelId: String? = null,
    /** What the default can be set to: the conversations' models, empty until read. */
    val models: List<EngineSelectableModel> = emptyList(),
    /** The engine's own default, named on the « the server's » choice. */
    val engineDefault: EngineSelectableModel? = null,
) {
    /** What the row says is set: the model's name, its raw id when the list does not name it. */
    val defaultModelLabel: String?
        get() = defaultModelId?.let { id -> models.firstOrNull { it.modelId == id }?.label ?: id }
}

/**
 * The engine shell's settings (D-077), laid out as Claude's since lot 4 (10/10/2026): the account
 * at the head, then groups of rows on raised cards — appearance (theme and text size, each a
 * segmented control), notifications, the assistant's instructions, its default model
 * (19/10/2026), usage and spending ceilings (12/10/2026), the platform's
 * addresses (read only: changing one is signing out and in again) — and signing out alone at the
 * foot.
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
    onAttentionSound: (Boolean) -> Unit = {},
    onLocalDictation: (Boolean) -> Unit = {},
    onChatFontSize: (ChatFontSize) -> Unit = {},
    onOpenBudget: () -> Unit = {},
    onDefaultModel: (String?) -> Unit = {},
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var pickModel by rememberSaveable { mutableStateOf(false) }

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
            onOpenBudget = onOpenBudget,
            onThemeMode = onThemeMode,
            onChatFontSize = onChatFontSize,
            onAttentionSound = onAttentionSound,
            onLocalDictation = onLocalDictation,
            onPickModel = { pickModel = true },
            onSignOut = { confirmSignOut = true },
        )
        if (pickModel) {
            DefaultModelDialog(
                state = state,
                onPick = { modelId ->
                    pickModel = false
                    onDefaultModel(modelId)
                },
                onDismiss = { pickModel = false },
            )
        }
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

/**
 * The default model, picked among the conversations' models, « the server's » first: the engine's
 * own default, which follows the deployment when it changes. Picking closes the dialog.
 */
@Composable
private fun DefaultModelDialog(
    state: EngineSettingsUiState,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.engine_settings_default_model)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(Res.string.engine_settings_default_model_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ModelOption(
                    label = serverModelLabel(state.engineDefault),
                    selected = state.defaultModelId == null,
                    onClick = { onPick(null) },
                )
                state.models.forEach { model ->
                    ModelOption(
                        label = model.label,
                        selected = model.modelId == state.defaultModelId,
                        onClick = { onPick(model.modelId) },
                    )
                }
                if (state.models.isEmpty()) {
                    Text(
                        stringResource(Res.string.engine_settings_default_model_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.engine_settings_cancel)) }
        },
    )
}

@Composable
private fun ModelOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun serverModelLabel(engineDefault: EngineSelectableModel?): String =
    engineDefault?.let { stringResource(Res.string.engine_settings_default_model_server_named, it.label) }
        ?: stringResource(Res.string.engine_settings_default_model_server)

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
    onOpenBudget: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onChatFontSize: (ChatFontSize) -> Unit,
    onAttentionSound: (Boolean) -> Unit,
    onLocalDictation: (Boolean) -> Unit,
    onPickModel: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        if (state.account.name != null || state.account.host != null) {
            AccountHeader(state.account)
        }
        Spacer(Modifier.height(8.dp))

        SectionLabel(stringResource(Res.string.engine_settings_appearance))
        SectionGroup {
            ChoiceRow(
                label = stringResource(Res.string.engine_settings_theme),
                options = ThemeMode.entries,
                selected = state.themeMode,
                optionLabel = { stringResource(it.label()) },
                onPick = onThemeMode,
            )
            SectionDivider()
            ChoiceRow(
                label = stringResource(Res.string.engine_settings_text_size),
                options = ChatFontSize.entries,
                selected = state.chatFontSize,
                optionLabel = { stringResource(it.label()) },
                onPick = onChatFontSize,
            )
        }
        Spacer(Modifier.height(GROUP_GAP))

        SectionLabel(stringResource(Res.string.engine_settings_notifications))
        SectionGroup {
            ToggleRow(
                icon = Icons.Outlined.NotificationsActive,
                headline = stringResource(Res.string.engine_settings_attention),
                supporting = stringResource(Res.string.engine_settings_attention_hint),
                checked = state.attentionSound,
                onCheckedChange = onAttentionSound,
                modifier = Modifier.testTag("settings_attention_sound"),
            )
        }
        Spacer(Modifier.height(GROUP_GAP))

        SectionLabel(stringResource(Res.string.engine_settings_dictation))
        SectionGroup {
            ToggleRow(
                icon = Icons.Outlined.Mic,
                headline = stringResource(Res.string.engine_settings_local_dictation),
                supporting = stringResource(Res.string.engine_settings_local_dictation_hint),
                checked = state.localDictation,
                onCheckedChange = onLocalDictation,
                modifier = Modifier.testTag("settings_local_dictation"),
            )
        }
        Spacer(Modifier.height(GROUP_GAP))

        SectionLabel(stringResource(Res.string.engine_settings_assistant))
        SectionGroup {
            NavigationRow(
                icon = Icons.Outlined.Description,
                headline = stringResource(Res.string.engine_settings_instructions),
                supporting = stringResource(Res.string.engine_settings_instructions_hint),
                onClick = onOpenInstructions,
                modifier = Modifier.testTag("settings_instructions"),
            )
            SectionDivider()
            NavigationRow(
                icon = Icons.Outlined.AutoAwesome,
                headline = stringResource(Res.string.engine_settings_default_model),
                supporting = state.defaultModelLabel ?: serverModelLabel(state.engineDefault),
                onClick = onPickModel,
                modifier = Modifier.testTag("settings_default_model"),
            )
            SectionDivider()
            NavigationRow(
                icon = Icons.Outlined.QueryStats,
                headline = stringResource(Res.string.engine_settings_usage),
                supporting = null,
                onClick = onOpenUsage,
            )
            SectionDivider()
            NavigationRow(
                icon = Icons.Outlined.Savings,
                headline = stringResource(Res.string.engine_settings_budget),
                supporting = stringResource(Res.string.engine_settings_budget_hint),
                onClick = onOpenBudget,
                modifier = Modifier.testTag("settings_budget"),
            )
        }
        Spacer(Modifier.height(GROUP_GAP))

        SectionLabel(stringResource(Res.string.engine_settings_platform))
        SectionGroup {
            ValueRow(stringResource(Res.string.engine_settings_engine), state.addresses?.baseUrl)
            SectionDivider()
            ValueRow(stringResource(Res.string.engine_settings_scheduler), state.addresses?.schedulerUrl)
            SectionDivider()
            ValueRow(stringResource(Res.string.engine_settings_portal), state.addresses?.issuerUrl)
        }
        Text(
            text = stringResource(Res.string.engine_settings_platform_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(GROUP_GAP))

        SectionGroup {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSignOut, role = Role.Button)
                    .heightIn(min = ROW_HEIGHT)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("settings_sign_out"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    stringResource(Res.string.engine_settings_sign_out),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** The person, as the drawer's foot shows them: the initial in a round mark, the name in the serif, the host under it. */
@Composable
private fun AccountHeader(account: DrawerAccount) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                account.initial,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column {
            Text(
                account.name ?: account.host.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (account.name != null && account.host != null) {
                Text(
                    account.host,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A setting with a few values, picked on a segmented control under its name. */
@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onPick: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onPick(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = {},
                    label = { Text(optionLabel(option), maxLines = 1, softWrap = false) },
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    headline: String,
    supporting: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .heightIn(min = ROW_HEIGHT)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        RowText(headline, supporting, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun NavigationRow(
    icon: ImageVector,
    headline: String,
    supporting: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .heightIn(min = ROW_HEIGHT)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        RowText(headline, supporting, Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RowText(headline: String, supporting: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(headline, style = MaterialTheme.typography.bodyLarge)
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A label on the left, what it is set to on the right, quiet. */
@Composable
private fun ValueRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value?.takeIf { it.isNotBlank() } ?: UNSET,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun ThemeMode.label(): StringResource = when (this) {
    ThemeMode.SYSTEM -> Res.string.engine_theme_system
    ThemeMode.LIGHT -> Res.string.engine_theme_light
    ThemeMode.DARK -> Res.string.engine_theme_dark
}

private fun ChatFontSize.label(): StringResource = when (this) {
    ChatFontSize.SMALL -> Res.string.engine_text_size_small
    ChatFontSize.MEDIUM -> Res.string.engine_text_size_medium
    ChatFontSize.LARGE -> Res.string.engine_text_size_large
}

private const val UNSET = "—"
private val ROW_HEIGHT = 56.dp
private val GROUP_GAP = 24.dp
