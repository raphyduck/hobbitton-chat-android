package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.engine.ConnectorOption
import com.garfiec.librechat.core.data.engine.offered
import com.garfiec.librechat.core.model.engine.EngineModelRef
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue
import com.garfiec.librechat.feature.tasks.components.ConnectorPickerSheet
import com.garfiec.librechat.feature.tasks.components.ModelPickerSheet
import com.garfiec.librechat.feature.tasks.components.TasksBottomSheet
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_cancel
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_no_connector
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
import com.garfiec.librechat.feature.tasks.resources.tasks_model_default_short
import com.garfiec.librechat.feature.tasks.resources.tasks_new
import com.garfiec.librechat.feature.tasks.resources.tasks_objective
import org.jetbrains.compose.resources.stringResource

/**
 * Creating a mission: an objective, the connectors it may use, and how it is watched.
 *
 * No profile choice, deliberately (25/08). What a mission does is what its objective says; what it
 * MAY do is what the connector picker grants — the per-session permission rules override the
 * profile's anyway. Every mission from this sheet runs on the server's generic `mission` profile.
 *
 * A socle is ticked by default, and the **scheduler** decides which — never a list written here.
 * Until 30/08/2026 nothing was ticked, on the reasoning that a mission which can do nothing is
 * harmless while one that writes to memory from a pre-filled checkbox is not. That held while the
 * app was a launcher for a handful of missions; it stopped holding when the app became the way to
 * work, and every mission started with a picker to open before it could do anything at all.
 *
 * What makes it safe is *which* connectors: the server's socle is reading only — memory, files, web
 * search, bank accounts, the schedule's state — and nothing that acts. What makes it bounded is
 * cost: every ticked connector reloads its tool catalogue to the model on every turn, so ticking
 * all thirty would spend a mission's budget before it did anything (D-040).
 *
 * The model, on the other hand, IS preselected — with the engine's own default, and never with a
 * first-in-the-list guess. An unticked connector means « you may not »; an unticked model would
 * mean nothing at all, since a mission always runs on *some* model. The honest preselection is
 * therefore what the engine would have picked anyway, which is also what makes the picker safe to
 * ignore.
 *
 * Both choices open the module's shared sheets — the same controls the conversation's chips open —
 * because two presentations of one choice is how the old dropdown and the conversation's radio
 * list came to disagree on what « no pick » even meant.
 */
@Composable
fun NewMissionSheet(
    onDismiss: () -> Unit,
    onLaunch: (
        objective: String,
        connectors: List<String>,
        autonomous: Boolean,
        model: EngineModelRef?,
    ) -> Unit,
    modifier: Modifier = Modifier,
    models: List<EngineSelectableModel> = emptyList(),
    preselectedModel: EngineSelectableModel? = null,
    catalogue: ConnectorCatalogue = ConnectorCatalogue(),
    catalogueFailed: Boolean = false,
) {
    // Saveable: a configuration change used to wipe a filled-in objective and every ticked box.
    var objective by rememberSaveable { mutableStateOf("") }
    var autonomous by rememberSaveable { mutableStateOf(true) }
    val ticked = rememberSaveable(saver = listSaver({ it.toList() }, { it.toMutableStateList() })) {
        mutableListOf<String>().toMutableStateList()
    }
    // Keyed on the preselection: the catalogue is fetched while the sheet is already open, so the
    // default arrives a moment late. Without the key the selection would stay null through that
    // arrival and the sheet would offer a list with nothing ticked.
    var model by remember(preselectedModel) { mutableStateOf(preselectedModel) }
    var pickingConnectors by rememberSaveable { mutableStateOf(false) }
    var pickingModel by rememberSaveable { mutableStateOf(false) }

    // Recomputed on the mode: what an autonomous mission may not tick depends on it.
    val offered = catalogue.offered(autonomous)

    // The socle, ticked once the catalogue lands — it is fetched while the sheet is already open,
    // so there is nothing to tick on the first composition.
    //
    // Once, and never again: `seeded` survives a configuration change alongside `ticked`, so a
    // rotation does not re-tick what was just unticked. Without it the seeding would fight the
    // person — the same shape as the model preselection's key, one state further on.
    var seeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(offered) {
        if (!seeded && offered.isNotEmpty()) {
            seeded = true
            // `enabled` is honoured here too: an autonomous mission must not open the sheet with a
            // connector already ticked that the server would refuse at launch.
            offered.filter { it.tickedByDefault && it.enabled }.forEach { option ->
                if (option.name !in ticked) ticked += option.name
            }
        }
    }

    TasksBottomSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        horizontalPadding = 24.dp,
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

        // Rendered only when the catalogue arrived. A field with an empty sheet behind it would
        // read as a list that failed to load, when in fact nothing is wrong: the mission runs on
        // the profile's model, as it did before this picker existed.
        if (models.isNotEmpty()) {
            PickerField(
                label = stringResource(Res.string.tasks_model),
                value = model?.label ?: stringResource(Res.string.tasks_model_default_short),
                onOpen = { pickingModel = true },
            )
        }

        // A field, not nineteen checkboxes: the catalogue's rows would push « Launch » three
        // screens down — the exact failure the mode selector had on 25/08. The choice lives in
        // its own sheet; this one only shows the count.
        ConnectorField(
            offered = offered,
            ticked = ticked,
            failed = catalogueFailed,
            loading = offered.isEmpty() && !catalogueFailed,
            onOpen = { pickingConnectors = true },
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

    if (pickingConnectors) {
        ConnectorPickerSheet(
            options = offered,
            ticked = ticked.toSet(),
            onToggle = { name -> if (name in ticked) ticked -= name else ticked += name },
            onDismiss = { pickingConnectors = false },
        )
    }
    if (pickingModel) {
        ModelPickerSheet(
            models = models,
            selected = model,
            onSelect = {
                model = it
                pickingModel = false
            },
            onDismiss = { pickingModel = false },
        )
    }
}

/** A read-only field that is really a button: shows the current value, opens the picker sheet. */
@Composable
private fun PickerField(label: String, value: String, onOpen: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        // Disabled so the tap lands on the modifier, not the text field — then recoloured so the
        // control does not LOOK switched off.
        enabled = false,
        label = { Text(label) },
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    )
}

/**
 * The connector count, and a way into the list. Nothing more: the creation sheet must fit on one
 * screen, launch button included.
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    // An empty list would read as « this mission can have nothing », which is a
                    // different claim — and the very outcome the fetched catalogue exists to fix.
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
                    stringResource(Res.string.tasks_connectors_count, ticked.size, offered.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
