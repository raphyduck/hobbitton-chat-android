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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import com.garfiec.librechat.core.data.engine.offered
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_cancel
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_tool_count
import com.garfiec.librechat.feature.tasks.resources.tasks_connectors
import com.garfiec.librechat.feature.tasks.resources.tasks_connectors_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_connectors_loading
import com.garfiec.librechat.feature.tasks.resources.tasks_launch
import com.garfiec.librechat.feature.tasks.resources.tasks_mode_autonomous
import com.garfiec.librechat.feature.tasks.resources.tasks_mode_hint_autonomous
import com.garfiec.librechat.feature.tasks.resources.tasks_mode_hint_interactive
import com.garfiec.librechat.feature.tasks.resources.tasks_mode_interactive
import com.garfiec.librechat.feature.tasks.resources.tasks_model
import com.garfiec.librechat.feature.tasks.resources.tasks_model_default
import com.garfiec.librechat.feature.tasks.resources.tasks_model_none
import com.garfiec.librechat.feature.tasks.resources.tasks_new
import com.garfiec.librechat.feature.tasks.resources.tasks_objective
import org.jetbrains.compose.resources.stringResource

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
    catalogue: ConnectorCatalogue = ConnectorCatalogue(),
    catalogueFailed: Boolean = false,
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

            // A field, not a row of chips, and the difference is whether the sheet fits.
            //
            // Nine chips wrap to three rows — 112 dp — and push the « Lancer » button past the
            // bottom of a 390x844 phone, with nothing on screen saying anything is below. That is
            // the same failure the mode selector had on 25/08: a sheet that scrolls, and a person
            // who cannot see that it does. One 56 dp field leaves the whole sheet on screen,
            // button included, and it is Material's own answer for a single choice among more
            // than five.
            //
            // Rendered only when the catalogue arrived. A field with an empty menu behind it
            // would read as a list that failed to load, when in fact nothing is wrong: the
            // mission runs on the profile's model, as it did before this picker existed.
            if (models.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = model?.label ?: stringResource(Res.string.tasks_model_none),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.tasks_model)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        // Only when nothing is picked: the sentence explains what happens then, and
                        // it is the one case a person needs telling. Under a chosen model it would
                        // be a permanent line of text saying nothing.
                        supportingText = if (model == null) {
                            { Text(stringResource(Res.string.tasks_model_default)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        models.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate.label) },
                                onClick = {
                                    model = candidate
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }

            Text(stringResource(Res.string.tasks_connectors), style = MaterialTheme.typography.titleSmall)
            // Fetched, never copied. This sheet used to hold its own list of four names — out of the
            // platform's nineteen — and the tool patterns behind « fichiers » named tools the engine
            // does not serve. A permission rule for a tool nobody offers is accepted in silence, so
            // the mission launched with an empty toolbox and said so mid-run (30/08/2026). The
            // catalogue now comes from the scheduler's own `CONNECTEURS`.
            val offered = catalogue.offered(autonomous)
            when {
                catalogueFailed -> Text(
                    stringResource(Res.string.tasks_connectors_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                offered.isEmpty() -> Text(
                    stringResource(Res.string.tasks_connectors_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> offered.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = option.name in ticked,
                            // Disabled rather than hidden: someone who wonders where shell went gets
                            // an answer from the mode hint below, not a missing row to puzzle over.
                            enabled = option.enabled,
                            onCheckedChange = { checked ->
                                if (checked) ticked += option.name else ticked -= option.name
                            },
                        )
                        Column {
                            Text(
                                option.name,
                                color = if (option.enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            )
                            // What the connector costs: every tool a session declares is re-sent to
                            // the model on every turn, and a mission has spent the bulk of its budget
                            // on a catalogue it never called (server-side D-040).
                            Text(
                                stringResource(Res.string.tasks_chat_tool_count, option.toolCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
                        // Ticking a connector then switching to autonomous would otherwise carry a
                        // permission the server is about to refuse — better to clear it here, where
                        // the person can see it happen. Which ones those are is the catalogue's
                        // answer, not a name written down here.
                        catalogue.offered(autonomous = true)
                            .filterNot { it.enabled }
                            .forEach { ticked -= it.name }
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
