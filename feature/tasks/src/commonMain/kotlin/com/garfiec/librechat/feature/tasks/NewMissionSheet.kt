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
import com.garfiec.librechat.feature.tasks.resources.tasks_model
import com.garfiec.librechat.feature.tasks.resources.tasks_model_default
import com.garfiec.librechat.feature.tasks.resources.tasks_new
import com.garfiec.librechat.feature.tasks.resources.tasks_objective
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
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
 *
 * The model, on the other hand, IS preselected — with the engine's own default, and never with a
 * first-in-the-list guess. An unticked connector means « you may not »; an unticked model would
 * mean nothing at all, since a mission always runs on *some* model. The honest preselection is
 * therefore what the engine would have picked anyway, which is also what makes the picker safe to
 * ignore.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NewMissionSheet(
    onDismiss: () -> Unit,
    onLaunch: (
        objective: String,
        connectors: List<String>,
        autonomous: Boolean,
        model: EngineModelRef?,
    ) -> Unit,
    models: List<EngineSelectableModel> = emptyList(),
    preselectedModel: EngineSelectableModel? = null,
) {
    var objective by remember { mutableStateOf("") }
    var autonomous by remember { mutableStateOf(true) }
    val ticked = remember { mutableListOf<String>().toMutableStateList() }
    // Keyed on the preselection: the catalogue is fetched while the sheet is already open, so the
    // default arrives a moment late. Without the key the selection would stay null through that
    // arrival and the sheet would offer a list with nothing ticked.
    var model by remember(preselectedModel) { mutableStateOf(preselectedModel) }

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

            // Rendered only when the catalogue arrived. A heading over an empty row would look
            // like a list that failed to load, when in fact nothing is wrong: the mission runs on
            // the profile's model, which is what it did before this picker existed.
            if (models.isNotEmpty()) {
                Text(stringResource(Res.string.tasks_model), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    models.forEach { candidate ->
                        FilterChip(
                            selected = candidate == model,
                            onClick = { model = candidate },
                            label = { Text(candidate.label) },
                        )
                    }
                }
                if (model == null) {
                    Text(
                        stringResource(Res.string.tasks_model_default),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    enabled = objective.isNotBlank(),
                    onClick = { onLaunch(objective.trim(), ticked.toList(), autonomous, model?.ref) },
                ) { Text(stringResource(Res.string.tasks_launch)) }
            }
        }
    }
}
