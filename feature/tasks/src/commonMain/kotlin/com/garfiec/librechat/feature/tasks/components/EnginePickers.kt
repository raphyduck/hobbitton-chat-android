package com.garfiec.librechat.feature.tasks.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.engine.ConnectorOption
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_tool_count
import com.garfiec.librechat.feature.tasks.resources.tasks_connector_default
import com.garfiec.librechat.feature.tasks.resources.tasks_connectors
import com.garfiec.librechat.feature.tasks.resources.tasks_model
import com.garfiec.librechat.feature.tasks.resources.tasks_model_default_short
import org.jetbrains.compose.resources.stringResource

/**
 * The connector choice, in its own sheet — the module's ONE copy.
 *
 * Both the conversation's chip and the new-mission form open this. The two used to carry their own
 * near-identical sheets, and they had already drifted: one greyed a barred connector's label, the
 * other only its checkbox, so `shell` looked tickable in the conversation and barred in the form.
 *
 * Each row carries its tool count because that is the connector's price: every declared tool is
 * re-sent to the model on every turn, and a mission has spent the bulk of its budget on a catalogue
 * it never called (server-side D-040).
 */
@Composable
internal fun ConnectorPickerSheet(
    options: List<ConnectorOption>,
    ticked: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    TasksBottomSheet(onDismiss = onDismiss) {
        Text(
            stringResource(Res.string.tasks_connectors),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        options.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = option.enabled) { onToggle(option.name) }
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = option.name in ticked,
                    // Greyed rather than hidden: whoever wonders where shell went gets an answer,
                    // instead of a missing row to puzzle over.
                    enabled = option.enabled,
                    onCheckedChange = { onToggle(option.name) },
                )
                Column(Modifier.padding(start = 4.dp)) {
                    Text(
                        option.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (option.enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                    Text(
                        // « 5 outils · par défaut » — the socle is ticked when the sheet opens, and
                        // a row that says so is the difference between a considered default and
                        // five boxes someone assumes they ticked by accident.
                        stringResource(Res.string.tasks_chat_tool_count, option.toolCount) +
                            if (option.tickedByDefault) {
                                " · " + stringResource(Res.string.tasks_connector_default)
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The model choice — the module's ONE copy, for both the conversation and the new-mission form.
 *
 * A sheet of radio rows, not a dropdown: the catalogue has more than five entries, which is the
 * same reason the connector list stopped being inline checkboxes. The nullable row is deliberate —
 * an unpicked model is not a missing setting, it is the engine's (or the profile's) own choice,
 * and taking a pick back must stay possible.
 */
@Composable
internal fun ModelPickerSheet(
    models: List<EngineSelectableModel>,
    selected: EngineSelectableModel?,
    onSelect: (EngineSelectableModel?) -> Unit,
    onDismiss: () -> Unit,
) {
    TasksBottomSheet(onDismiss = onDismiss) {
        Text(
            stringResource(Res.string.tasks_model),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        ModelRow(
            label = stringResource(Res.string.tasks_model_default_short),
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        models.forEach { candidate ->
            ModelRow(
                label = candidate.label,
                selected = candidate == selected,
                onClick = { onSelect(candidate) },
            )
        }
    }
}

@Composable
private fun ModelRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
