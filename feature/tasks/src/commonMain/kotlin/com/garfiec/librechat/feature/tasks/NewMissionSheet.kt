package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.engine.ConnectorOption
import com.garfiec.librechat.core.data.engine.offered
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_cancel
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_no_connector
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_tool_count
import com.garfiec.librechat.feature.tasks.resources.tasks_connectors
import com.garfiec.librechat.feature.tasks.resources.tasks_connectors_count
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
/**
 * Le décompte, et de quoi ouvrir la liste. Rien de plus : la feuille de création doit tenir sur un
 * écran, bouton « Lancer » compris.
 */
@Composable
private fun ConnectorField(
    offered: List<ConnectorOption>,
    ticked: List<String>,
    failed: Boolean,
    loading: Boolean,
    onOpen: () -> Unit,
) {
    val enabled = !failed && !loading
    Column {
        Text(stringResource(Res.string.tasks_connectors), style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onOpen)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    // Une liste vide se lirait « cette mission ne peut rien avoir », ce qui est une
                    // autre affirmation — et le résultat qu'on vient justement de corriger.
                    failed -> stringResource(Res.string.tasks_connectors_failed)
                    loading -> stringResource(Res.string.tasks_connectors_loading)
                    ticked.isEmpty() -> stringResource(Res.string.tasks_chat_no_connector)
                    else -> ticked.sorted().joinToString(", ")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    failed -> MaterialTheme.colorScheme.error
                    ticked.isEmpty() || loading -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            if (enabled) {
                Text(
                    stringResource(
                        Res.string.tasks_connectors_count,
                        ticked.size,
                        offered.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Le choix des connecteurs, dans sa propre feuille.
 *
 * Chaque ligne porte son nombre d'outils, parce que c'est son prix : tout outil déclaré repart au
 * modèle à chaque tour, et une mission a déjà brûlé l'essentiel de son budget sur un catalogue
 * qu'elle n'appelait pas (D-040 côté serveur).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ConnectorPickerSheet(
    offered: List<ConnectorOption>,
    ticked: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            Text(
                stringResource(Res.string.tasks_connectors),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            offered.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = option.enabled) { onToggle(option.name) }
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = option.name in ticked,
                        // Grisé plutôt que masqué : qui se demande où est passé shell obtient une
                        // réponse, au lieu d'une ligne manquante sur laquelle s'interroger.
                        enabled = option.enabled,
                        onCheckedChange = { onToggle(option.name) },
                    )
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(
                            option.name,
                            color = if (option.enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        )
                        Text(
                            stringResource(Res.string.tasks_chat_tool_count, option.toolCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

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
    var picking by remember { mutableStateOf(false) }

    // Recalculé sur le mode : ce qu'une mission autonome ne peut pas cocher en dépend.
    val offered = catalogue.offered(autonomous)

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

            // Un CHAMP, pas dix-neuf cases.
            //
            // Le catalogue est passé de quatre connecteurs à dix-neuf le 30/08 : la liste faisait
            // alors défiler la feuille sur trois écrans et repoussait « Lancer » hors de vue — la
            // panne exacte que le sélecteur de modèle avait eue le 25/08, et pour laquelle il était
            // déjà devenu un champ. Le choix vit donc dans sa propre feuille, et celle-ci ne montre
            // que le décompte.
            ConnectorField(
                offered = offered,
                ticked = ticked,
                failed = catalogueFailed,
                loading = offered.isEmpty() && !catalogueFailed,
                onOpen = { picking = true },
            )

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

    if (picking) {
        ConnectorPickerSheet(
            offered = offered,
            ticked = ticked,
            onToggle = { name -> if (name in ticked) ticked -= name else ticked += name },
            onDismiss = { picking = false },
        )
    }
}
