package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.scheduler.Consumption
import com.garfiec.librechat.core.model.scheduler.ModelConsumption
import com.garfiec.librechat.core.model.scheduler.Provider
import com.garfiec.librechat.core.model.scheduler.ProviderHealth
import com.garfiec.librechat.feature.tasks.components.DisclosureRow
import com.garfiec.librechat.feature.tasks.components.Explanation
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_back
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_all_ok
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_check
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_checking
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_failing
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_header
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_none
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_unknown
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_at_least
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_by_model
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_cache_saved
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_calls
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_header
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_tiny
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_total
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_total_partial
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_unit_price
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_unpriced
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_unpriced_note
import com.garfiec.librechat.feature.tasks.resources.usage_not_configured
import com.garfiec.librechat.feature.tasks.resources.usage_not_configured_hint
import com.garfiec.librechat.feature.tasks.resources.usage_title
import com.garfiec.librechat.feature.tasks.util.byCostDescending
import com.garfiec.librechat.feature.tasks.util.groupThousands
import com.garfiec.librechat.feature.tasks.util.money
import com.garfiec.librechat.feature.tasks.util.observedPricePerMillion
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The week's spend and the providers' health — under Settings, as Claude keeps its usage.
 *
 * Both sections are the ones that led the Tasks tab until 24/09/2026, moved unchanged: the spend
 * still refuses to print a misleading zero, the provider check still waits for a press because it
 * costs money. Only where they live changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsageViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.usage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.tasks_chat_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (!state.loading && !state.schedulerConfigured) {
                Explanation(
                    title = stringResource(Res.string.usage_not_configured),
                    hint = stringResource(Res.string.usage_not_configured_hint),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.consumption?.let { report ->
                        item(key = "spend-header") { UsageSectionHeader(stringResource(Res.string.tasks_spend_header)) }
                        item(key = "spend-section") { SpendSection(report) }
                    }
                    if (state.schedulerConfigured) {
                        item(key = "providers-header") {
                            UsageSectionHeader(stringResource(Res.string.tasks_providers_header))
                        }
                        item(key = "providers-section") {
                            ProvidersSection(
                                health = state.providers,
                                checking = state.providersChecking,
                                failure = state.providersError,
                                onCheck = viewModel::checkProviders,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageSectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/**
 * Which providers still answer — and the reason this one has a button.
 *
 * Every other section on this screen loads itself. This one does not, because obtaining it calls
 * every model in the catalogue for real: about $0.0015 and two to three seconds, measured
 * server-side rather than guessed. An answer that changes roughly once a month has no business
 * being re-bought on every glance at the tab.
 *
 * So the honest default is « unknown », said out loud, with the price of finding out written next
 * to the button. A screen that quietly spends money when it appears is one nobody can reason about.
 */
@Composable
private fun ProvidersSection(
    health: ProviderHealth?,
    checking: Boolean,
    failure: String?,
    onCheck: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                checking -> Text(
                    stringResource(Res.string.tasks_providers_checking),
                    style = MaterialTheme.typography.bodyMedium,
                )

                // No figure before the first check, on purpose: the exact cost comes back
                // *with* the answer, and a number hardcoded here would go stale in silence the
                // day the catalogue grows. « A few tenths of a cent » is true and stays true.
                health == null -> Text(
                    stringResource(Res.string.tasks_providers_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                health.providers.isEmpty() -> Text(
                    stringResource(Res.string.tasks_providers_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                else -> {
                    Text(
                        if (health.allHealthy) {
                            stringResource(
                                Res.string.tasks_providers_all_ok, health.providers.size,
                            )
                        } else {
                            stringResource(
                                Res.string.tasks_providers_failing, health.failing.size,
                            )
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (health.allHealthy) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    HorizontalDivider()
                    health.providers.forEach { ProviderRow(it) }
                }
            }

            failure?.let {
                Text(
                    stringResource(Res.string.tasks_providers_failed, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            TextButton(onClick = onCheck, enabled = !checking) {
                Text(stringResource(Res.string.tasks_providers_check))
            }
        }
    }
}

@Composable
private fun ProviderRow(provider: Provider) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            provider.name + (provider.baseUrl?.let { "  ($it)" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = if (provider.isHealthy) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        provider.models.forEach { model ->
            Text(
                if (model.isHealthy) {
                    model.name
                } else {
                    // The provider's own sentence, and its status: « 401 — Invalid API key » says
                    // what to do next, where a red dot says only that something is wrong.
                    model.name +
                        (model.httpStatus?.let { " [$it]" } ?: "") +
                        " — " + (model.error ?: "")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (model.isHealthy) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/**
 * The week's spend, by model.
 *
 * Three states share this list and must not be confusable, which is the whole reason this screen
 * exists in the form it does:
 *
 * - a real amount — « 13.0373 $ » ;
 * - a real amount too small to print at four decimals — « < 0,0001 $ », never « 0.0000 $ » ;
 * - **no price at all** — « non tarifé », because the gateway writes a literal zero for a model
 *   its price table does not know, and rendering that as free would be a lie about precisely the
 *   cheap models one routes traffic to in order to save money.
 *
 * When any model is unpriced the total is a floor, and the header says « at least ». A total
 * presented as exact when terms are missing is worse than no total.
 */
@Composable
private fun SpendSection(report: Consumption) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val tokens = groupThousands(report.totalTokens)
            Text(
                if (report.isComplete) {
                    stringResource(Res.string.tasks_spend_total, money(report.totalSpend), tokens)
                } else {
                    stringResource(
                        Res.string.tasks_spend_total_partial, money(report.totalSpend), tokens,
                    )
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (report.cacheSavings > 0) {
                Text(
                    stringResource(Res.string.tasks_spend_cache_saved, money(report.cacheSavings)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (report.models.isNotEmpty()) {
                // The per-model breakdown is the long part of this card — eight-plus rows that push
                // the schedule below the fold. Collapsed by default so the week's total, the one
                // number this section exists to answer, sits alone at the top of the tab. Saveable
                // so scrolling the card out of the LazyColumn does not re-collapse an opened list.
                var detailShown by rememberSaveable { mutableStateOf(false) }
                HorizontalDivider()
                DisclosureRow(
                    label = stringResource(Res.string.tasks_spend_by_model, report.models.size),
                    expanded = detailShown,
                    onToggle = { detailShown = !detailShown },
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (detailShown) {
                    report.models.byCostDescending().forEach { model ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.model, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    listOfNotNull(
                                        groupThousands(model.tokens),
                                        stringResource(Res.string.tasks_spend_calls, model.calls),
                                        // What the million actually cost: the figure that explains
                                        // why the row above it is ranked where it is. Absent rather
                                        // than guessed when the spend is unknown or partial.
                                        model.observedPricePerMillion()?.let { rate ->
                                            stringResource(Res.string.tasks_spend_unit_price, money(rate))
                                        },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                modelAmount(model),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (model.isPriced) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    // Dimmed, not red: « no price » is not an error, it is an unknown.
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    if (!report.isComplete) {
                        Text(
                            stringResource(Res.string.tasks_spend_unpriced_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun modelAmount(model: ModelConsumption): String {
    val spend = model.spend
    return when {
        spend == null -> stringResource(Res.string.tasks_spend_unpriced)
        // A positive amount that rounds to zero at four decimals: printing « 0.0000 $ » here would
        // put back the misleading zero the server takes such care to remove. Seen in service on
        // 23/08 — deepseek/deepseek-chat had cost 0.00000572 $.
        spend > 0 && money(spend) == money(0.0) -> stringResource(Res.string.tasks_spend_tiny)
        // « at least », like the header total: part of this model's spend has no price, so the
        // amount is a floor. Saying the number without the reserve would be the misleading zero
        // in another costume — a figure that looks complete and is not.
        model.isPartial -> stringResource(Res.string.tasks_spend_at_least, money(spend))
        else -> money(spend)
    }
}
