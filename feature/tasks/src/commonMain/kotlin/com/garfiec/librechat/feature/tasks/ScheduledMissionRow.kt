package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.scheduler.ScheduledMission
import com.garfiec.librechat.feature.tasks.components.TasksBottomSheet
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_cancel
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_actions
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_cron
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_cron_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_delete
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_delete_body
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_delete_title
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_disable
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_edit
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_enable
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_last_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_last_ok
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_never
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_next
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_once
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_recurring
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_run
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_runat
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_runat_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_save
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_suspended
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_timezone
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_tools
import com.garfiec.librechat.feature.tasks.resources.tasks_state_running
import com.garfiec.librechat.feature.tasks.util.groupThousands
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One scheduled mission, as a row of a group (lot 4, 10/10/2026): a dot for its state, the name,
 * when it runs and how the last run went, and a « ⋮ » that opens its actions in a sheet — run now,
 * suspend or resume, reschedule, delete. The row itself opens the mission's runs.
 *
 * The four text buttons the card carried (30/08/2026) are the sheet's rows now: a dozen missions
 * with four buttons each was a wall of verbs, and the destructive one sat on every card.
 */
@Composable
internal fun ScheduledMissionRow(
    mission: ScheduledMission,
    onOpen: () -> Unit,
    onRun: () -> Unit,
    onToggle: () -> Unit,
    onReschedule: (cron: String?, runAt: String?) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var acting by rememberSaveable(mission.name) { mutableStateOf(false) }
    var editing by rememberSaveable(mission.name) { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable(mission.name) { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusDot(color = mission.dotColour(), label = mission.stateLabel())
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                mission.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                mission.scheduleLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LastRunLine(mission)
        }
        IconButton(onClick = { acting = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.tasks_scheduled_actions, mission.name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (acting) {
        ActionsSheet(
            mission = mission,
            onDismiss = { acting = false },
            onRun = {
                acting = false
                onRun()
            },
            onToggle = {
                acting = false
                onToggle()
            },
            onEdit = {
                acting = false
                editing = true
            },
            onDelete = {
                acting = false
                confirmingDelete = true
            },
        )
    }
    if (editing) {
        RescheduleSheet(
            mission = mission,
            onDismiss = { editing = false },
            onSave = { cron, runAt ->
                editing = false
                onReschedule(cron, runAt)
            },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(Res.string.tasks_scheduled_delete_title, mission.name)) },
            // Ce que la suppression emporte, et ce qu'elle n'emporte pas : l'historique reste, et
            // c'est la seule question qu'on se pose avant de confirmer.
            text = { Text(stringResource(Res.string.tasks_scheduled_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(Res.string.tasks_scheduled_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(Res.string.tasks_cancel))
                }
            },
        )
    }
}

/** « 0 7 * * 1-5 · 14 outils par tour · Prochaine 2026-10-13T07:00 », or « Suspendue », or « En cours ». */
@Composable
private fun ScheduledMission.scheduleLine(): String {
    val state = when {
        running -> stringResource(Res.string.tasks_state_running)
        !enabled -> stringResource(Res.string.tasks_scheduled_suspended)
        nextRun != null -> stringResource(Res.string.tasks_scheduled_next, nextRun!!)
        else -> null
    }
    return listOfNotNull(
        cron ?: runAt,
        pluralStringResource(Res.plurals.tasks_scheduled_tools, declaredTools, declaredTools),
        state,
    ).joinToString(" · ")
}

@Composable
private fun ScheduledMission.stateLabel(): String = when {
    running -> stringResource(Res.string.tasks_state_running)
    !enabled -> stringResource(Res.string.tasks_scheduled_suspended)
    lastRun?.succeeded == false -> stringResource(Res.string.tasks_scheduled_last_failed, lastRun?.stopReason.orEmpty())
    else -> name
}

/** The accent while it runs, the hairline grey when suspended, red after a failed run, quiet otherwise. */
@Composable
private fun ScheduledMission.dotColour(): Color = when {
    running -> MaterialTheme.colorScheme.primary
    !enabled -> MaterialTheme.colorScheme.outlineVariant
    lastRun?.succeeded == false -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
}

/**
 * The mission's actions, in a sheet under its name. A mission already running is not started
 * twice: the scheduler refuses it anyway (server-side D-041), and offering the row would make that
 * refusal look like a bug rather than a rule.
 */
@Composable
private fun ActionsSheet(
    mission: ScheduledMission,
    onDismiss: () -> Unit,
    onRun: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    TasksBottomSheet(onDismiss = onDismiss) {
        Text(
            mission.name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        SheetAction(
            icon = Icons.Outlined.PlayArrow,
            label = stringResource(Res.string.tasks_scheduled_run),
            onClick = onRun,
            enabled = !mission.running,
        )
        if (mission.enabled) {
            SheetAction(
                icon = Icons.Outlined.PauseCircle,
                label = stringResource(Res.string.tasks_scheduled_disable),
                onClick = onToggle,
            )
        } else {
            SheetAction(
                icon = Icons.Outlined.PlayCircle,
                label = stringResource(Res.string.tasks_scheduled_enable),
                onClick = onToggle,
            )
        }
        SheetAction(
            icon = Icons.Outlined.Schedule,
            label = stringResource(Res.string.tasks_scheduled_edit),
            onClick = onEdit,
        )
        SheetAction(
            icon = Icons.Outlined.Delete,
            label = stringResource(Res.string.tasks_scheduled_delete),
            onClick = onDelete,
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val colour = if (enabled) tint else MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colour, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colour)
    }
}

/**
 * Changer quand une mission part — l'horaire, ou la date unique.
 *
 * Seul le champ modifié voyage : `modifier` fusionne côté serveur. Renvoyer la mission entière
 * n'était pas une option — `etat` ne publie ni le prompt ni la liste d'outils, donc un écran qui
 * reconstruirait la mission depuis ce qu'il affiche viderait le prompt au premier report. C'est la
 * panne des connecteurs recopiés, un écran plus loin.
 *
 * Les deux champs s'excluent, comme côté serveur : une mission est récurrente OU ponctuelle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RescheduleSheet(
    mission: ScheduledMission,
    onDismiss: () -> Unit,
    onSave: (cron: String?, runAt: String?) -> Unit,
) {
    var recurring by rememberSaveable { mutableStateOf(mission.runAt == null) }
    var cron by rememberSaveable { mutableStateOf(mission.cron.orEmpty()) }
    var runAt by rememberSaveable { mutableStateOf(mission.runAt.orEmpty()) }

    TasksBottomSheet(
        onDismiss = onDismiss,
        horizontalPadding = 24.dp,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(mission.name, style = MaterialTheme.typography.titleLarge)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = recurring,
                onClick = { recurring = true },
                label = { Text(stringResource(Res.string.tasks_scheduled_recurring)) },
            )
            FilterChip(
                selected = !recurring,
                onClick = { recurring = false },
                label = { Text(stringResource(Res.string.tasks_scheduled_once)) },
            )
        }

        if (recurring) {
            OutlinedTextField(
                value = cron,
                onValueChange = { cron = it },
                label = { Text(stringResource(Res.string.tasks_scheduled_cron)) },
                supportingText = { Text(stringResource(Res.string.tasks_scheduled_cron_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = runAt,
                onValueChange = { runAt = it },
                label = { Text(stringResource(Res.string.tasks_scheduled_runat)) },
                supportingText = { Text(stringResource(Res.string.tasks_scheduled_runat_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Le fuseau de la mission, pas celui du téléphone : c'est dans celui-là que le serveur
        // lira l'heure saisie, et les deux diffèrent en voyage.
        if (mission.timeZone.isNotBlank()) {
            Text(
                stringResource(Res.string.tasks_scheduled_timezone, mission.timeZone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.tasks_cancel)) }
            TextButton(
                enabled = if (recurring) cron.isNotBlank() else runAt.isNotBlank(),
                onClick = {
                    if (recurring) onSave(cron.trim(), null) else onSave(null, runAt.trim())
                },
            ) { Text(stringResource(Res.string.tasks_scheduled_save)) }
        }
    }
}

/**
 * How the last run went, in one line.
 *
 * `succeeded` is null *while the mission runs*, and that third state is why this is not a boolean:
 * showing a red failure on a mission that is working would be worse than showing nothing.
 */
@Composable
private fun LastRunLine(mission: ScheduledMission) {
    val last = mission.lastRun
    when {
        last == null -> Text(
            stringResource(Res.string.tasks_scheduled_never),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        last.succeeded == false -> Text(
            stringResource(
                Res.string.tasks_scheduled_last_failed,
                last.stopReason.orEmpty(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        last.succeeded == true -> Text(
            stringResource(
                Res.string.tasks_scheduled_last_ok,
                last.startedAt.orEmpty(),
                groupThousands((last.tokens ?: 0).toLong()),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
