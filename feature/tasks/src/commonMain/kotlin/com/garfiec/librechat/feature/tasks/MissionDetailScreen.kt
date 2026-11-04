package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.engine.missionDisplayName
import com.garfiec.librechat.core.model.scheduler.MissionEdits
import com.garfiec.librechat.core.model.scheduler.ScheduledMission
import com.garfiec.librechat.core.ui.components.SectionGroup
import com.garfiec.librechat.feature.tasks.components.Explanation
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_cancel
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_back
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_budget
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_ceiling
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_connectors
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_discreet
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_load_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_model
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_model_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_model_pick
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_notify
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_open_run
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_profile
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_prompt
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_save_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_saved
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_schedule
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_settings
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_tier
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_tier_none
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_timeout
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_tokens_run
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_tools
import com.garfiec.librechat.feature.tasks.resources.tasks_mission_tools_follow
import com.garfiec.librechat.feature.tasks.resources.tasks_retry
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_cron
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_cron_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_delete
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_delete_body
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_delete_title
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_disable
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_enable
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_once
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_recurring
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_run
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_runat
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_runat_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_save
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_timezone
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One scheduled mission in full, to read and to change (04/11/2026, Raphaël: « voir plus de
 * détails sur la tâche, l'éditer : quel modèle la fait tourner, quel palier, son prompt »).
 *
 * The screen keeps a draft of the editable fields; « Enregistrer » sends only what differs from
 * the mission as last read (`MissionEdits`), the scheduler merges, and the mission is read again.
 * The actions of the list (run, suspend, delete) are here too, so one place answers every
 * question about a mission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionDetailScreen(
    name: String,
    onBack: () -> Unit,
    onOpenConversation: (sessionId: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MissionDetailViewModel = koinViewModel { parametersOf(MissionDetailArgs(name)) },
) {
    // Collected whatever the entry's lifecycle says, as the conversation does (see MissionChatScreen).
    val state by viewModel.state.collectAsState()
    val mission = state.mission
    var draft by remember(mission) { mutableStateOf(mission?.let { MissionDraft.of(it) }) }
    val snackbar = remember { SnackbarHostState() }
    val savedWords = stringResource(Res.string.tasks_mission_saved)
    val failedWords = stringResource(Res.string.tasks_mission_save_failed)
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbar.showSnackbar(
            when (notice) {
                MissionNotice.Saved -> savedWords
                is MissionNotice.Failed -> failedWords.replace("%1\$s", notice.detail)
            },
        )
        viewModel.noticeShown()
    }
    // The effect keys on the deletion alone: a new `onBack` lambda must not restart it.
    val goBack by rememberUpdatedState(onBack)
    LaunchedEffect(state.deleted) { if (state.deleted) goBack() }

    val edits = draft?.let { current -> mission?.let { current.editsFrom(it) } }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(missionDisplayName(name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.tasks_chat_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        enabled = edits != null && !edits.isEmpty && !state.saving,
                        onClick = { edits?.let(viewModel::save) },
                    ) { Text(stringResource(Res.string.tasks_scheduled_save)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = draft
        when {
            state.loading && mission == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            mission == null || current == null -> Explanation(
                title = stringResource(Res.string.tasks_mission_load_failed),
                hint = state.error,
                modifier = Modifier.padding(padding),
                action = stringResource(Res.string.tasks_retry) to viewModel::load,
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MissionStatus(mission, onOpenConversation = onOpenConversation)
                MissionActions(
                    mission = mission,
                    onRun = viewModel::run,
                    onToggle = { viewModel.setEnabled(!mission.enabled) },
                    onDelete = viewModel::delete,
                )
                MissionEditor(
                    mission = mission,
                    draft = current,
                    models = state.models,
                    onChange = { draft = it },
                )
            }
        }
    }
}

/** How the mission stands: its schedule, its profile and tools, its last run. */
@Composable
private fun MissionStatus(mission: ScheduledMission, onOpenConversation: (String, String) -> Unit) {
    SectionGroup {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(mission.scheduleLine(), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.tasks_mission_profile, mission.profile),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LastRunLine(mission)
            mission.lastRun?.let { last ->
                val tokens = last.tokens
                val duration = last.durationSeconds
                if (tokens != null || duration != null) {
                    Text(
                        stringResource(
                            Res.string.tasks_mission_tokens_run,
                            tokens?.toString() ?: "?",
                            duration?.toInt()?.toString() ?: "?",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                last.sessionId?.let { sessionId ->
                    TextButton(onClick = { onOpenConversation(sessionId, missionDisplayName(mission.name)) }) {
                        Text(stringResource(Res.string.tasks_mission_open_run))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissionActions(
    mission: ScheduledMission,
    onRun: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onRun, enabled = !mission.running) { Text(stringResource(Res.string.tasks_scheduled_run)) }
        TextButton(onClick = onToggle) {
            val label = if (mission.enabled) Res.string.tasks_scheduled_disable else Res.string.tasks_scheduled_enable
            Text(stringResource(label))
        }
        TextButton(onClick = { confirmingDelete = true }) {
            Text(stringResource(Res.string.tasks_scheduled_delete), color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(Res.string.tasks_scheduled_delete_title, missionDisplayName(mission.name))) },
            text = { Text(stringResource(Res.string.tasks_scheduled_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                ) { Text(stringResource(Res.string.tasks_scheduled_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(Res.string.tasks_cancel)) }
            },
        )
    }
}

/** The fields a person may change: schedule, model and tier, prompt, limits, switches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissionEditor(
    mission: ScheduledMission,
    draft: MissionDraft,
    models: List<String>,
    onChange: (MissionDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.tasks_mission_schedule), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.recurring,
                onClick = { onChange(draft.copy(recurring = true)) },
                label = { Text(stringResource(Res.string.tasks_scheduled_recurring)) },
            )
            FilterChip(
                selected = !draft.recurring,
                onClick = { onChange(draft.copy(recurring = false)) },
                label = { Text(stringResource(Res.string.tasks_scheduled_once)) },
            )
        }
        if (draft.recurring) {
            OutlinedTextField(
                value = draft.cron,
                onValueChange = { onChange(draft.copy(cron = it)) },
                label = { Text(stringResource(Res.string.tasks_scheduled_cron)) },
                supportingText = { Text(stringResource(Res.string.tasks_scheduled_cron_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = draft.runAt,
                onValueChange = { onChange(draft.copy(runAt = it)) },
                label = { Text(stringResource(Res.string.tasks_scheduled_runat)) },
                supportingText = { Text(stringResource(Res.string.tasks_scheduled_runat_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (mission.timeZone.isNotBlank()) {
            Text(
                stringResource(Res.string.tasks_scheduled_timezone, mission.timeZone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(stringResource(Res.string.tasks_mission_settings), style = MaterialTheme.typography.titleSmall)
        ModelField(value = draft.model, models = models, onChange = { onChange(draft.copy(model = it)) })
        Text(stringResource(Res.string.tasks_mission_tier), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf<String?>(null) + TIERS).forEach { tier ->
                FilterChip(
                    selected = draft.tier == tier,
                    onClick = { onChange(draft.copy(tier = tier)) },
                    label = { Text(tier ?: stringResource(Res.string.tasks_mission_tier_none)) },
                )
            }
        }
        OutlinedTextField(
            value = draft.prompt,
            onValueChange = { onChange(draft.copy(prompt = it)) },
            label = { Text(stringResource(Res.string.tasks_mission_prompt)) },
            minLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.tokenBudget,
            onValueChange = { onChange(draft.copy(tokenBudget = it.filter(Char::isDigit))) },
            label = { Text(stringResource(Res.string.tasks_mission_budget)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.timeoutSeconds,
            onValueChange = { onChange(draft.copy(timeoutSeconds = it.filter(Char::isDigit))) },
            label = { Text(stringResource(Res.string.tasks_mission_timeout)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.toolCallCeiling,
            onValueChange = { onChange(draft.copy(toolCallCeiling = it.filter(Char::isDigit))) },
            label = { Text(stringResource(Res.string.tasks_mission_ceiling)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow(stringResource(Res.string.tasks_mission_notify), draft.notifies) {
            onChange(draft.copy(notifies = it))
        }
        SwitchRow(stringResource(Res.string.tasks_mission_discreet), draft.discreet) {
            onChange(draft.copy(discreet = it))
        }

        Text(stringResource(Res.string.tasks_mission_connectors), style = MaterialTheme.typography.titleSmall)
        Text(
            mission.connectors.joinToString(", ").ifEmpty { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(Res.string.tasks_mission_tools), style = MaterialTheme.typography.titleSmall)
        Text(
            mission.tools?.joinToString(", ")?.ifEmpty { "—" }
                ?: stringResource(Res.string.tasks_mission_tools_follow),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** The model id, typed or picked among the gateway's; empty, the mission follows its tier. */
@Composable
private fun ModelField(value: String, models: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(stringResource(Res.string.tasks_mission_model)) },
            supportingText = { Text(stringResource(Res.string.tasks_mission_model_hint)) },
            singleLine = true,
            trailingIcon = {
                if (models.isNotEmpty()) {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(Res.string.tasks_mission_model_pick),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        expanded = false
                        onChange(model)
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private val TIERS = listOf("bas", "moyen", "haut")

/**
 * The editable fields as text, the way the screen holds them; [editsFrom] turns what differs
 * from the mission into the only fields sent.
 */
internal data class MissionDraft(
    val recurring: Boolean,
    val cron: String,
    val runAt: String,
    val prompt: String,
    val model: String,
    val tier: String?,
    val tokenBudget: String,
    val timeoutSeconds: String,
    val toolCallCeiling: String,
    val notifies: Boolean,
    val discreet: Boolean,
) {
    fun editsFrom(mission: ScheduledMission): MissionEdits {
        val scheduleChanged = recurring != (mission.runAt == null) ||
            (recurring && cron.trim() != mission.cron.orEmpty()) ||
            (!recurring && runAt.trim() != mission.runAt.orEmpty())
        return MissionEdits(
            cron = cron.trim().takeIf { scheduleChanged && recurring && it.isNotEmpty() },
            runAt = runAt.trim().takeIf { scheduleChanged && !recurring && it.isNotEmpty() },
            prompt = prompt.takeIf { it != mission.prompt.orEmpty() && it.isNotBlank() },
            model = model.trim().takeIf { it != mission.model.orEmpty() && it.isNotEmpty() },
            tier = tier.takeIf { it != mission.tier && it != null },
            tokenBudget = tokenBudget.toIntOrNull()?.takeIf { it != mission.tokenBudget },
            timeoutSeconds = timeoutSeconds.toIntOrNull()?.takeIf { it != mission.timeoutSeconds },
            toolCallCeiling = toolCallCeiling.toIntOrNull()?.takeIf { it != mission.toolCallCeiling },
            notifies = notifies.takeIf { it != mission.notifies },
            discreet = discreet.takeIf { it != mission.discreet },
        )
    }

    companion object {
        fun of(mission: ScheduledMission) = MissionDraft(
            recurring = mission.runAt == null,
            cron = mission.cron.orEmpty(),
            runAt = mission.runAt.orEmpty(),
            prompt = mission.prompt.orEmpty(),
            model = mission.model.orEmpty(),
            tier = mission.tier,
            tokenBudget = mission.tokenBudget.toString(),
            timeoutSeconds = mission.timeoutSeconds.toString(),
            toolCallCeiling = mission.toolCallCeiling?.toString().orEmpty(),
            notifies = mission.notifies,
            discreet = mission.discreet,
        )
    }
}
