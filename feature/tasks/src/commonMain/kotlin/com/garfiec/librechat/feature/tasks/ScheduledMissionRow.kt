package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.scheduler.ScheduledMission
import com.garfiec.librechat.feature.tasks.components.TasksBottomSheet
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_cancel
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
 * One recurring mission: when it next runs, how its last run went, and the two things worth doing
 * to it from a phone — start it now, or suspend it.
 *
 * The tool count is shown next to the budget rather than hidden in settings: it is that number,
 * multiplied by the turns, that decides whether a mission fits its budget (server-side D-040), and
 * seeing it is what makes an expensive mission obvious before the bill does.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduledMissionRow(
    mission: ScheduledMission,
    onOpen: () -> Unit,
    onRun: () -> Unit,
    onToggle: () -> Unit,
    onReschedule: (cron: String?, runAt: String?) -> Unit,
    onDelete: () -> Unit,
) {
    var editing by rememberSaveable(mission.name) { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable(mission.name) { mutableStateOf(false) }
    // The card opens the mission's runs, as a task opens its history in Claude; the buttons keep
    // their own gestures on top of it.
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(mission.name, style = MaterialTheme.typography.titleMedium)

            Text(
                listOfNotNull(
                    mission.profile,
                    mission.cron ?: mission.runAt,
                    pluralStringResource(
                        Res.plurals.tasks_scheduled_tools,
                        mission.declaredTools,
                        mission.declaredTools,
                    ),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                mission.running ->
                    Text(
                        stringResource(Res.string.tasks_state_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                !mission.enabled ->
                    Text(
                        stringResource(Res.string.tasks_scheduled_suspended),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )

                mission.nextRun != null ->
                    Text(
                        stringResource(Res.string.tasks_scheduled_next, mission.nextRun!!),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }

            LastRunLine(mission)

            // FlowRow, not Row: the four labels do not fit a phone's width, and a Row divides the
            // shortfall among them rather than admitting it. The last button was left a handful of
            // pixels and rendered « Delete » as a column of single letters (reported 30/08/2026).
            // Wrapping onto a second line costs a row of height and keeps every action readable —
            // including the destructive one, the worst of the four to leave illegible.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // A mission already running is not started twice: the scheduler refuses it anyway
                // (server-side D-041), and offering the button would make that refusal look like a
                // bug rather than a rule.
                TextButton(onClick = onRun, enabled = !mission.running) {
                    ActionLabel(stringResource(Res.string.tasks_scheduled_run))
                }
                TextButton(onClick = onToggle) {
                    ActionLabel(
                        stringResource(
                            if (mission.enabled) {
                                Res.string.tasks_scheduled_disable
                            } else {
                                Res.string.tasks_scheduled_enable
                            },
                        ),
                    )
                }
                TextButton(onClick = { editing = true }) {
                    ActionLabel(stringResource(Res.string.tasks_scheduled_edit))
                }
                TextButton(
                    onClick = { confirmingDelete = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    ActionLabel(stringResource(Res.string.tasks_scheduled_delete))
                }
            }
        }
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
 * One action's caption. Single line and unwrappable on purpose: a caption that wraps inside a button
 * is the symptom of a row that does not fit, and letting it wrap hides the overflow instead of
 * letting the layout resolve it.
 */
@Composable
private fun ActionLabel(text: String) {
    Text(text, maxLines = 1, softWrap = false)
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
        )

        last.succeeded == true -> Text(
            stringResource(
                Res.string.tasks_scheduled_last_ok,
                last.startedAt.orEmpty(),
                groupThousands((last.tokens ?: 0).toLong()),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
