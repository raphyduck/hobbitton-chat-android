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
import com.garfiec.librechat.feature.tasks.resources.tasks_profile
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
 * Creating a mission: an objective, a profile, the connectors it may use, and how it is watched.
 *
 * Nothing is ticked by default. A mission that can do nothing is useless but harmless; one that can
 * write to memory because a checkbox was pre-filled is neither.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NewMissionSheet(
    profiles: List<String>,
    onDismiss: () -> Unit,
    onLaunch: (profile: String, objective: String, connectors: List<String>, autonomous: Boolean) -> Unit,
) {
    var objective by remember { mutableStateOf("") }
    var profile by remember(profiles) { mutableStateOf(profiles.firstOrNull().orEmpty()) }
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

            Text(stringResource(Res.string.tasks_profile), style = MaterialTheme.typography.titleSmall)
            // `FlowRow` et non `Row` : une `Row` ne passe pas à la ligne, elle COMPRIME. Avec
            // cinq profils, la dernière puce se retrouvait large d'un caractère et son texte
            // s'écrivait verticalement, une lettre par ligne — et les profils suivants
            // n'existaient plus à l'écran, donc étaient inatteignables.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                profiles.forEach { candidate ->
                    FilterChip(
                        selected = candidate == profile,
                        onClick = { profile = candidate },
                        label = { Text(candidate) },
                    )
                }
            }

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
                    enabled = objective.isNotBlank() && profile.isNotBlank(),
                    onClick = { onLaunch(profile, objective.trim(), ticked.toList(), autonomous) },
                ) { Text(stringResource(Res.string.tasks_launch)) }
            }
        }
    }
}
