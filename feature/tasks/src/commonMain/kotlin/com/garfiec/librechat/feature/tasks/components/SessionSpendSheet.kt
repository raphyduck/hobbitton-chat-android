package com.garfiec.librechat.feature.tasks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.engine.EngineSelectableModel
import com.garfiec.librechat.core.model.scheduler.PlatformRoles
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_role_clerk
import com.garfiec.librechat.feature.tasks.resources.tasks_role_coder
import com.garfiec.librechat.feature.tasks.resources.tasks_role_navigator
import com.garfiec.librechat.feature.tasks.resources.tasks_role_other
import com.garfiec.librechat.feature.tasks.resources.tasks_role_reader
import com.garfiec.librechat.feature.tasks.resources.tasks_role_savant
import com.garfiec.librechat.feature.tasks.resources.tasks_role_scout
import com.garfiec.librechat.feature.tasks.resources.tasks_role_thinker
import com.garfiec.librechat.feature.tasks.resources.tasks_spend
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_calls
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_children
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_empty
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_estimated
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_loading
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_tokens
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_tools
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_total
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_unpriced
import com.garfiec.librechat.feature.tasks.util.ModelSignature
import com.garfiec.librechat.feature.tasks.util.SessionSpend
import com.garfiec.librechat.feature.tasks.util.SpendLine
import com.garfiec.librechat.feature.tasks.util.compactTokens
import com.garfiec.librechat.feature.tasks.util.formatDollars
import com.garfiec.librechat.feature.tasks.util.shortModelLabel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A role's name as the transcript and the spend sheet print it, in lower case (08/11/2026): the
 * signature under an answer reads « penseur · Opus 5.5 », a role is a word, not a title. Null for
 * a role the table does not name, which the callers print as « autre ».
 */
@Composable
internal fun roleLabel(role: String?): String = when (role) {
    PlatformRoles.THINKER -> stringResource(Res.string.tasks_role_thinker)
    PlatformRoles.SCOUT -> stringResource(Res.string.tasks_role_scout)
    PlatformRoles.SAVANT -> stringResource(Res.string.tasks_role_savant)
    PlatformRoles.NAVIGATOR -> stringResource(Res.string.tasks_role_navigator)
    PlatformRoles.CODER -> stringResource(Res.string.tasks_role_coder)
    PlatformRoles.CLERK -> stringResource(Res.string.tasks_role_clerk)
    PlatformRoles.READER -> stringResource(Res.string.tasks_role_reader)
    null -> stringResource(Res.string.tasks_role_other)
    else -> role
}

/** The model as the catalogue names it, short, or its raw id when the catalogue does not list it. */
internal fun modelLabel(modelId: String, models: List<EngineSelectableModel>): String =
    models.firstOrNull { it.modelId.equals(modelId, ignoreCase = true) }?.label?.let(::shortModelLabel) ?: modelId

/**
 * « penseur · Opus 5.5 »: who wrote an answer, role first, the model after it. The role carries
 * the meaning (D-086: a conversation is led by a role), the model names the day's choice.
 */
@Composable
internal fun signatureText(signature: ModelSignature?, models: List<EngineSelectableModel>): String? =
    signature?.let { "${roleLabel(it.role)} · ${modelLabel(it.modelId, models)}" }

/** The small caption of a signature: `labelSmall`, muted, never louder than the answer above it. */
@Composable
internal fun SignatureCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}

/** The second small button of the top bar, beside the context ring: opens [SessionSpendSheet]. */
@Composable
internal fun SpendButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Outlined.PieChart,
            contentDescription = stringResource(Res.string.tasks_spend),
            modifier = Modifier.size(SPEND_ICON_SIZE),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The conversation's spend by role and model (08/11/2026, Raphaël): calls, tool calls, tokens and
 * dollars per model under its role, the sub-agents' sessions folded in, the total at the bottom.
 * The figures are the engine's; a line the engine could not price is the app's estimate from the
 * tokens and the gateway's table, and the sheet says so once.
 */
@Composable
internal fun SessionSpendSheet(
    spend: SessionSpend?,
    loading: Boolean,
    failed: Boolean,
    models: List<EngineSelectableModel>,
    onDismiss: () -> Unit,
) {
    TasksBottomSheet(onDismiss = onDismiss, horizontalPadding = 24.dp, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.tasks_spend), style = MaterialTheme.typography.titleMedium)
        when {
            loading && spend == null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(Res.string.tasks_spend_loading), style = MaterialTheme.typography.bodyMedium)
            }
            failed && spend == null -> Text(
                stringResource(Res.string.tasks_spend_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            spend == null || spend.lines.isEmpty() -> Text(
                stringResource(Res.string.tasks_spend_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> SpendTable(spend, models)
        }
        Text(
            stringResource(Res.string.tasks_spend_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpendTable(spend: SessionSpend, models: List<EngineSelectableModel>) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            var lastRole: String? = null
            var first = true
            spend.lines.forEach { line ->
                if (first || line.role != lastRole) {
                    if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        roleLabel(line.role),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                SpendRow(line, models)
                lastRole = line.role
                first = false
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.tasks_spend_total),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    spend.total?.let(::formatDollars) ?: stringResource(Res.string.tasks_spend_unpriced),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                listOfNotNull(
                    pluralStringResource(Res.plurals.tasks_spend_calls, spend.calls, spend.calls),
                    pluralStringResource(Res.plurals.tasks_spend_tools, spend.tools, spend.tools),
                    spend.children.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.tasks_spend_children, it, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (spend.estimated) {
                Text(
                    stringResource(Res.string.tasks_spend_estimated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SpendRow(line: SpendLine, models: List<EngineSelectableModel>) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(modelLabel(line.modelId, models), style = MaterialTheme.typography.bodyLarge)
            Text(
                listOf(
                    pluralStringResource(Res.plurals.tasks_spend_calls, line.calls, line.calls),
                    pluralStringResource(Res.plurals.tasks_spend_tools, line.tools, line.tools),
                    stringResource(
                        Res.string.tasks_spend_tokens,
                        compactTokens(line.tokens.input + (line.tokens.cache?.read ?: 0) + (line.tokens.cache?.write ?: 0)),
                        compactTokens(line.tokens.output + line.tokens.reasoning),
                    ),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            line.cost?.let { (if (line.estimated) "≈ " else "") + formatDollars(it) }
                ?: stringResource(Res.string.tasks_spend_unpriced),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private val SPEND_ICON_SIZE = 20.dp
