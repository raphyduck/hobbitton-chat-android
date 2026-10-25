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
import com.garfiec.librechat.core.model.scheduler.ModelPrice
import com.garfiec.librechat.core.model.scheduler.ModelPrices
import com.garfiec.librechat.core.ui.components.modelPriceLabel
import com.garfiec.librechat.core.ui.resources.model_price_legend
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_tool_count
import com.garfiec.librechat.feature.tasks.resources.tasks_connector_default
import com.garfiec.librechat.feature.tasks.resources.tasks_connectors
import com.garfiec.librechat.feature.tasks.resources.tasks_connectors_act
import com.garfiec.librechat.feature.tasks.resources.tasks_connectors_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_connectors_read
import com.garfiec.librechat.feature.tasks.resources.tasks_model
import com.garfiec.librechat.feature.tasks.resources.tasks_model_auto_long
import com.garfiec.librechat.feature.tasks.resources.tasks_model_default_short
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.garfiec.librechat.core.ui.resources.Res as CoreRes

/**
 * The model choice — the module's ONE copy, for both the conversation and the new-mission form.
 *
 * A sheet of radio rows, not a dropdown: the catalogue has more than five entries, which is the
 * same reason the connector list once stopped being inline checkboxes. The nullable row is deliberate —
 * an unpicked model is not a missing setting, it is the engine's (or the profile's) own choice,
 * and taking a pick back must stay possible.
 */
@Composable
internal fun ModelPickerSheet(
    models: List<EngineSelectableModel>,
    selected: EngineSelectableModel?,
    prices: ModelPrices,
    onSelect: (EngineSelectableModel?) -> Unit,
    onDismiss: () -> Unit,
) {
    TasksBottomSheet(onDismiss = onDismiss) {
        Text(
            stringResource(Res.string.tasks_model),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (prices.models.isNotEmpty()) {
            // What the figure in each row means, said once. « 3 / 15 $/M » is unreadable without
            // it, and repeating « input / output per million » on every row would drown the names.
            Text(
                stringResource(CoreRes.string.model_price_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        ModelRow(
            label = stringResource(Res.string.tasks_model_default_short),
            price = null,
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        // « Auto » (25/10/2026, Raphaël's rule: the model necessary and sufficient): the scheduler
        // reads the first message and picks the tier; the conversation then keeps that model.
        ModelRow(
            label = stringResource(Res.string.tasks_model_auto_long),
            price = null,
            selected = selected?.isAuto == true,
            onClick = { onSelect(EngineSelectableModel.AUTO) },
        )
        models.forEach { candidate ->
            ModelRow(
                label = candidate.label,
                // Keyed on the model id, not on the label: the engine's label is « Claude Opus 5 »
                // where the gateway prices « claude-opus-5 », and matching on prose would find
                // nothing while looking exactly like a model the gateway has no price for.
                price = prices.byModel[candidate.modelId.lowercase()],
                selected = candidate == selected,
                onClick = { onSelect(candidate) },
            )
        }
    }
}

@Composable
private fun ModelRow(
    label: String,
    price: ModelPrice?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
        modelPriceLabel(price)?.let { tag ->
            Text(
                tag,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The connector choice, in its own sheet: the module's ONE copy, opened from the composer's chip.
 *
 * Direct connectors only (Raphaël's rule, 24/10/2026): memory, workspace, web, shell, annuaire,
 * scheduler, alerts, the ones the engine reaches itself and whose ticks become the session's rules.
 * Whatever the annuaire serves (mail, messaging, browser, bank, SSH…) is the scheduler's decision,
 * from WHO speaks to the session (D-083), and is not listed here. Two groups since D-082: what
 * reads and searches, then what acts in the person's name ([ConnectorOption.actsForYou]).
 *
 * Each row carries its tool count because that is the connector's price: every declared tool is
 * re-sent to the model on every turn (server-side D-040).
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
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
        )
        Text(
            stringResource(Res.string.tasks_connectors_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
        )
        val (acting, reading) = options.partition { it.actsForYou }
        ConnectorGroup(Res.string.tasks_connectors_read, reading, ticked, onToggle)
        ConnectorGroup(Res.string.tasks_connectors_act, acting, ticked, onToggle)
    }
}

@Composable
private fun ConnectorGroup(
    label: StringResource,
    options: List<ConnectorOption>,
    ticked: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (options.isEmpty()) return
    Text(
        stringResource(label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 2.dp),
    )
    options.forEach { option ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(option.name) }
                .padding(horizontal = 24.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = option.name in ticked,
                onCheckedChange = { onToggle(option.name) },
            )
            Column(Modifier.padding(start = 4.dp)) {
                Text(
                    option.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    // « 5 outils · par défaut »: a row that says so is the difference between a
                    // considered default and a box someone assumes they ticked by accident.
                    pluralStringResource(Res.plurals.tasks_chat_tool_count, option.toolCount, option.toolCount) +
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
