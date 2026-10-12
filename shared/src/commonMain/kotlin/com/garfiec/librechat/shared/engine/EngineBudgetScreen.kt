package com.garfiec.librechat.shared.engine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.shared.resources.Res
import com.garfiec.librechat.shared.resources.engine_budget_cancel
import com.garfiec.librechat.shared.resources.engine_budget_day
import com.garfiec.librechat.shared.resources.engine_budget_day_over_week
import com.garfiec.librechat.shared.resources.engine_budget_hint
import com.garfiec.librechat.shared.resources.engine_budget_invalid
import com.garfiec.librechat.shared.resources.engine_budget_load_failed
import com.garfiec.librechat.shared.resources.engine_budget_month
import com.garfiec.librechat.shared.resources.engine_budget_order_hint
import com.garfiec.librechat.shared.resources.engine_budget_retry
import com.garfiec.librechat.shared.resources.engine_budget_save
import com.garfiec.librechat.shared.resources.engine_budget_save_failed
import com.garfiec.librechat.shared.resources.engine_budget_title
import com.garfiec.librechat.shared.resources.engine_budget_unavailable
import com.garfiec.librechat.shared.resources.engine_budget_week
import com.garfiec.librechat.shared.resources.engine_budget_week_over_month
import com.garfiec.librechat.shared.resources.engine_settings_back
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The conversations' spending ceilings (12/10/2026), opened from the settings. Closes on [onClose]
 * once saved, or at once on Annuler / back: what was typed then is dropped.
 */
@Composable
internal fun EngineBudgetScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EngineBudgetViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)

    LaunchedEffect(state.saved) {
        if (state.saved) close()
    }

    EngineBudgetContent(
        state = state,
        actions = BudgetActions(
            onDay = viewModel::setDay,
            onWeek = viewModel::setWeek,
            onMonth = viewModel::setMonth,
            onSave = viewModel::save,
            onRetry = viewModel::load,
            onCancel = onClose,
        ),
        modifier = modifier,
    )
}

/** What the person can do on this screen, kept together so the content's signature stays short. */
internal data class BudgetActions(
    val onDay: (String) -> Unit = {},
    val onWeek: (String) -> Unit = {},
    val onMonth: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EngineBudgetContent(
    state: EngineBudgetUiState,
    actions: BudgetActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.engine_budget_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onCancel) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.engine_settings_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (state.stored != null) {
                BudgetActionsBar(
                    canSave = !state.saving,
                    saving = state.saving,
                    onSave = actions.onSave,
                    onCancel = actions.onCancel,
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.stored == null -> BudgetUnavailable(state, padding, actions.onRetry)
            else -> BudgetForm(state, padding, actions)
        }
    }
}

/** Nothing to edit: a scheduler that predates the route, none at all, or one that did not answer. */
@Composable
private fun BudgetUnavailable(state: EngineBudgetUiState, padding: PaddingValues, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                if (state.loadFailed) Res.string.engine_budget_load_failed else Res.string.engine_budget_unavailable,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.loadFailed) {
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.engine_budget_retry)) }
        }
    }
}

@Composable
private fun BudgetForm(state: EngineBudgetUiState, padding: PaddingValues, actions: BudgetActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.engine_budget_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AmountField(state.day, actions.onDay, Res.string.engine_budget_day, "budget_day")
        AmountField(state.week, actions.onWeek, Res.string.engine_budget_week, "budget_week")
        AmountField(state.month, actions.onMonth, Res.string.engine_budget_month, "budget_month", ImeAction.Done)
        Text(
            text = stringResource(Res.string.engine_budget_order_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val error = state.problem?.let { stringResource(it.message()) }
            ?: state.refusal
            ?: if (state.saveFailed) stringResource(Res.string.engine_budget_save_failed) else null
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("budget_error"),
            )
        }
    }
}

/** An amount in dollars: a decimal keyboard, the unit after the number. */
@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: StringResource,
    tag: String,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        suffix = { Text(DOLLAR) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

/** Annuler and Enregistrer, kept above the keyboard. */
@Composable
private fun BudgetActionsBar(
    canSave: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.engine_budget_cancel)) }
            Button(onClick = onSave, enabled = canSave, modifier = Modifier.testTag("budget_save")) {
                if (saving) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                }
                Text(stringResource(Res.string.engine_budget_save))
            }
        }
    }
}

private fun BudgetProblem.message(): StringResource = when (this) {
    BudgetProblem.INVALID_AMOUNT -> Res.string.engine_budget_invalid
    BudgetProblem.DAY_OVER_WEEK -> Res.string.engine_budget_day_over_week
    BudgetProblem.WEEK_OVER_MONTH -> Res.string.engine_budget_week_over_month
}

private const val DOLLAR = "$"
