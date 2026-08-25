package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_cancel
import com.garfiec.librechat.feature.tasks.resources.tasks_connectors
import com.garfiec.librechat.feature.tasks.resources.tasks_launch
import com.garfiec.librechat.feature.tasks.resources.tasks_mode_autonomous
import com.garfiec.librechat.feature.tasks.resources.tasks_mode_hint_autonomous
import com.garfiec.librechat.feature.tasks.resources.tasks_mode_hint_interactive
import com.garfiec.librechat.feature.tasks.resources.tasks_mode_interactive
import com.garfiec.librechat.feature.tasks.resources.tasks_new
import com.garfiec.librechat.feature.tasks.resources.tasks_objective
import org.jetbrains.compose.resources.stringResource

/**
 * The connectors a mission can be given, in the order they are least to most dangerous.
 *
 * Read-only memory first, writing next, files after that, shell last — because that is the order in
 * which someone ticking boxes should meet them. The names mirror the server's
 * `scheduler/moteur.py`; the two must not drift.
 */
private val CONNECTORS = listOf("memoire", "memoire-ecriture", "fichiers", "shell")

/**
 * Creating a mission: an objective, the connectors it may use, and how it is watched.
 *
 * No profile choice, deliberately (25/08). What a mission does is what its objective says; what it
 * MAY do is what these checkboxes grant — the per-session permission rules override the profile's
 * anyway. The profile had become a third control that decided neither, offered the engine's
 * internal agents (`compaction`) alongside the real ones, and pushed the launch button off screen.
 * Every mission from this sheet runs on the server's generic `mission` profile.
 *
 * Nothing is ticked by default. A mission that can do nothing is useless but harmless; one that can
 * write to memory because a checkbox was pre-filled is neither.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NewMissionSheet(
    onDismiss: () -> Unit,
    onLaunch: (objective: String, connectors: List<String>, autonomous: Boolean) -> Unit,
) {
    var objective by remember { mutableStateOf("") }
    var autonomous by remember { mutableStateOf(true) }
    val ticked = remember { mutableListOf<String>().toMutableStateList() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            // Défilable, sinon le bouton « Lancer » est hors de l'écran et la mission est
            // impossible à créer depuis un téléphone. Constaté le 25/08 : la feuille s'arrêtait
            // au milieu du choix de mode, sans rien pour aller plus bas.
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.tasks_new), style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = objective,
                onValueChange = { objective = it },
                label = { Text(stringResource(Res.string.tasks_objective)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(Res.string.tasks_connectors), style = MaterialTheme.typography.titleSmall)
            CONNECTORS.forEach { connector ->
                val enabled = !(autonomous && connector == "shell")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = connector in ticked,
                        // Disabled rather than hidden: someone who wonders where shell went gets an
                        // answer from the mode hint below, instead of a missing row to puzzle over.
                        enabled = enabled,
                        onCheckedChange = { checked ->
                            if (checked) ticked += connector else ticked -= connector
                        },
                    )
                    Text(connector, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = autonomous,
                    onClick = {
                        autonomous = true
                        // Ticking shell then switching to autonomous would otherwise carry a
                        // permission the server is about to refuse — better to clear it here, where
                        // the person can see it happen.
                        ticked -= "shell"
                    },
                    label = { Text(stringResource(Res.string.tasks_mode_autonomous)) },
                )
                FilterChip(
                    selected = !autonomous,
                    onClick = { autonomous = false },
                    label = { Text(stringResource(Res.string.tasks_mode_interactive)) },
                )
            }
            Text(
                stringResource(
                    if (autonomous) Res.string.tasks_mode_hint_autonomous else Res.string.tasks_mode_hint_interactive,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.tasks_cancel)) }
                TextButton(
                    enabled = objective.isNotBlank(),
                    onClick = { onLaunch(objective.trim(), ticked.toList(), autonomous) },
                ) { Text(stringResource(Res.string.tasks_launch)) }
            }
        }
    }
}
