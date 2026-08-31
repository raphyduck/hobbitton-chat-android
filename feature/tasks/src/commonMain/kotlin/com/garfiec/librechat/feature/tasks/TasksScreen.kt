package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.engine.Mission
import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.model.engine.MissionState
import com.garfiec.librechat.core.model.scheduler.Consumption
import com.garfiec.librechat.core.model.scheduler.ModelConsumption
import com.garfiec.librechat.core.model.scheduler.Provider
import com.garfiec.librechat.core.model.scheduler.ProviderHealth
import com.garfiec.librechat.core.model.scheduler.ScheduledMission
import com.garfiec.librechat.feature.tasks.components.DisclosureRow
import com.garfiec.librechat.feature.tasks.components.Explanation
import com.garfiec.librechat.feature.tasks.components.TasksBottomSheet
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_cancel
import com.garfiec.librechat.feature.tasks.resources.tasks_empty
import com.garfiec.librechat.feature.tasks.resources.tasks_empty_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_new
import com.garfiec.librechat.feature.tasks.resources.tasks_not_configured
import com.garfiec.librechat.feature.tasks.resources.tasks_not_configured_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_all_ok
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_check
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_checking
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_failing
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_header
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_none
import com.garfiec.librechat.feature.tasks.resources.tasks_providers_unknown
import com.garfiec.librechat.feature.tasks.resources.tasks_retry
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_cron
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_cron_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_delete
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_delete_body
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_delete_title
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_disable
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_edit
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_enable
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_header
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_last_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_last_ok
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_never
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_next
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_once
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_recurring
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_run
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_runat
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_runat_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_save
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_suspended
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_timezone
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_tools
import com.garfiec.librechat.feature.tasks.resources.tasks_sessions_header
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_open
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_title
import com.garfiec.librechat.feature.tasks.resources.tasks_sign_in
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_at_least
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_by_model
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_cache_saved
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_calls
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_header
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_tiny
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_total
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_total_partial
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_unpriced
import com.garfiec.librechat.feature.tasks.resources.tasks_spend_unpriced_note
import com.garfiec.librechat.feature.tasks.resources.tasks_state_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_state_idle
import com.garfiec.librechat.feature.tasks.resources.tasks_state_running
import com.garfiec.librechat.feature.tasks.resources.tasks_state_succeeded
import com.garfiec.librechat.feature.tasks.resources.tasks_stop
import com.garfiec.librechat.feature.tasks.resources.tasks_title
import com.garfiec.librechat.feature.tasks.util.groupThousands
import com.garfiec.librechat.feature.tasks.util.hint
import com.garfiec.librechat.feature.tasks.util.missionAge
import com.garfiec.librechat.feature.tasks.util.money
import com.garfiec.librechat.feature.tasks.util.sentence
import com.garfiec.librechat.feature.tasks.util.title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Tasks tab: every mission the engine knows about, and what each one is doing.
 *
 * One list, not two. The brief's v9 merged « Tasks » and « Code » because the split was about how a
 * mission is watched, not about what it is — a mission is an objective handed to a profile, and
 * whether a human follows it live is one of its attributes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    modifier: Modifier = Modifier,
    onOpenMissionChat: (sessionId: String, title: String) -> Unit = { _, _ -> },
    viewModel: TasksViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The portal round trip needs a browser, and Compose already knows how to open one on both
    // platforms. Reaching for a platform launcher here would make this screen Android-only for the
    // sake of one call.
    val uriHandler = LocalUriHandler.current
    var composing by remember { mutableStateOf(false) }
    var configuring by remember { mutableStateOf(false) }
    // Collapsed by default: nine recurring missions push the sessions — what someone opens the tab
    // to read — below the fold. Hoisted to the screen rather than kept in the header, because it
    // decides whether the LazyColumn emits the rows at all; a lazy item's own state cannot.
    var scheduledShown by rememberSaveable { mutableStateOf(false) }
    // Read once into a local: `state` is a delegated property, so the branch below cannot smart-cast
    // through it.
    val failure = state.error
    // A spinner owns the screen only while there is genuinely nothing to show. Past that the pull
    // indicator carries the refresh — otherwise a pull on a tab showing only the schedule would
    // blank the very list the finger is on.
    val nothingToShow = state.missions.isEmpty() && state.scheduled.isEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.tasks_title)) },
                actions = {
                    // Reachable whether or not the engine is set up: changing a password or moving
                    // to another host must not require first getting into the « not configured »
                    // state, which is exactly when someone can no longer get there.
                    IconButton(onClick = { configuring = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(Res.string.tasks_settings_title),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.engineConfigured) {
                ExtendedFloatingActionButton(
                    onClick = { composing = true },
                    text = { Text(stringResource(Res.string.tasks_new)) },
                    icon = {},
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && !nothingToShow,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                // « Not set up » is not « broken ». Offering a retry here would send someone to check
                // their network over a settings form they have never filled in.
                !state.engineConfigured -> Explanation(
                    title = stringResource(Res.string.tasks_not_configured),
                    hint = stringResource(Res.string.tasks_not_configured_hint),
                    action = stringResource(Res.string.tasks_settings_open) to { configuring = true },
                )

                state.loading && nothingToShow ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }

                failure != null && state.missions.isEmpty() -> Explanation(
                    title = stringResource(failure.title()),
                    // What the last sign-in attempt ran into outranks the generic hint: it is the more
                    // recent and the more specific of the two answers to « why ».
                    hint = state.signInProblem?.let { stringResource(it.sentence()) }
                        ?: failure.hint()?.let { stringResource(it) },
                    // The offer follows the cause. « Retry » in front of an expired session is a button
                    // that cannot work, and it is the one someone will press five times before
                    // suspecting their settings.
                    action = when (failure) {
                        // Going through the portal is the remedy, and it was missing entirely until
                        // 24 August: the tab offered the settings form, so the only thing anyone could
                        // do about a missing token was retype a password that had nothing to do with it.
                        EngineFailureKind.AUTHENTICATION ->
                            stringResource(Res.string.tasks_sign_in) to
                                { viewModel.signIn(uriHandler::openUri) }
                        EngineFailureKind.NOT_FOUND ->
                            stringResource(Res.string.tasks_settings_open) to { configuring = true }
                        EngineFailureKind.PERMISSION -> null
                        else -> stringResource(Res.string.tasks_retry) to viewModel::refresh
                    },
                    secondary = if (failure == EngineFailureKind.AUTHENTICATION) {
                        stringResource(Res.string.tasks_settings_open) to { configuring = true }
                    } else {
                        null
                    },
                    busy = state.signingIn,
                )

                // « No session yet » is not « nothing to show »: the nine missions that run every
                // night are still there, and hiding them behind an empty sessions list was exactly
                // what made all of that work invisible from a phone.
                state.missions.isEmpty() && state.scheduled.isEmpty() -> Explanation(
                    title = stringResource(Res.string.tasks_empty),
                    hint = stringResource(Res.string.tasks_empty_hint),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // The week's spend leads, above the schedule: it is the one number that
                    // answers « where am I », and it fits in two lines. Everything below it is
                    // detail by comparison.
                    state.consumption?.let { report ->
                        item(key = "spend-header") {
                            SectionHeader(stringResource(Res.string.tasks_spend_header))
                        }
                        item(key = "spend-section") { SpendSection(report) }
                    }

                    // Providers come after the money and before the schedule: knowing a provider is
                    // dead changes how you read everything below it.
                    if (state.schedulerConfigured) {
                        item(key = "providers-header") {
                            SectionHeader(stringResource(Res.string.tasks_providers_header))
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

                    // The schedule leads among the rest: what runs tonight without anyone watching is
                    // what someone opens this tab to check. Sessions are the record of what happened.
                    if (state.scheduled.isNotEmpty()) {
                        item(key = "scheduled-header") {
                            // The count rides along so a collapsed section still says how much it
                            // is hiding — « Missions programmées 9 » reads as a fact, an empty
                            // heading as a bug.
                            DisclosureRow(
                                label = stringResource(Res.string.tasks_scheduled_header),
                                expanded = scheduledShown,
                                onToggle = { scheduledShown = !scheduledShown },
                                labelStyle = MaterialTheme.typography.titleSmall,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                trailing = {
                                    Text(
                                        state.scheduled.size.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                        }
                        if (scheduledShown) {
                            items(state.scheduled, key = { "scheduled-" + it.name }) { mission ->
                                ScheduledMissionRow(
                                    mission = mission,
                                    onRun = { viewModel.runScheduled(mission.name) },
                                    onToggle = {
                                        viewModel.setScheduledEnabled(mission.name, !mission.enabled)
                                    },
                                    onReschedule = { cron, runAt ->
                                        viewModel.rescheduleMission(mission.name, cron, runAt)
                                    },
                                    onDelete = { viewModel.deleteScheduled(mission.name) },
                                )
                            }
                        }
                    }
                    if (state.missions.isNotEmpty()) {
                        item(key = "sessions-header") {
                            SectionHeader(stringResource(Res.string.tasks_sessions_header))
                        }
                        items(state.missions, key = { it.sessionId }) { mission ->
                            MissionRow(
                                mission = mission,
                                onOpenChat = { onOpenMissionChat(mission.sessionId, mission.title) },
                                onStop = { viewModel.abort(mission.sessionId) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (configuring) {
        EngineSettingsSheet(
            onDismiss = { configuring = false },
            onSave = {
                configuring = false
                // The tab decided « not configured » from a store that has just changed; without
                // this it keeps that verdict until the screen is left and re-entered, which reads
                // as the form having done nothing.
                viewModel.refresh()
            },
        )
    }

    if (composing) {
        // The catalogue is asked for when the sheet appears, not with the rest of the tab: it is
        // 11,8 kB for something that changes about once a month, and it is useless anywhere else.
        // `LaunchedEffect(Unit)` rather than a call in the composition — a body that runs on every
        // recomposition would re-ask on each keystroke in the objective field.
        LaunchedEffect(Unit) {
            viewModel.loadModels()
            viewModel.loadConnectors()
        }
        NewMissionSheet(
            onDismiss = { composing = false },
            onLaunch = { objective, connectors, autonomous, model ->
                composing = false
                viewModel.launch(objective, connectors, autonomous, model)
            },
            models = state.models,
            preselectedModel = state.preselectedModel,
            catalogue = state.catalogue,
            catalogueFailed = state.connectorsFailed,
        )
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
                    report.models.forEach { model ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.model, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    groupThousands(model.tokens) + " · " +
                                        stringResource(Res.string.tasks_spend_calls, model.calls),
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

@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/**
 * One recurring mission: when it next runs, how its last run went, and the two things worth doing
 * to it from a phone — start it now, or suspend it.
 *
 * The tool count is shown next to the budget rather than hidden in settings: it is that number,
 * multiplied by the turns, that decides whether a mission fits its budget (server-side D-040), and
 * seeing it is what makes an expensive mission obvious before the bill does.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduledMissionRow(
    mission: ScheduledMission,
    onRun: () -> Unit,
    onToggle: () -> Unit,
    onReschedule: (cron: String?, runAt: String?) -> Unit,
    onDelete: () -> Unit,
) {
    var editing by rememberSaveable(mission.name) { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable(mission.name) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(mission.name, style = MaterialTheme.typography.titleMedium)

            Text(
                listOfNotNull(
                    mission.profile,
                    mission.cron ?: mission.runAt,
                    stringResource(Res.string.tasks_scheduled_tools, mission.declaredTools),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                mission.running ->
                    Text(
                        stringResource(Res.string.tasks_state_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                !mission.enabled ->
                    Text(
                        stringResource(Res.string.tasks_scheduled_suspended),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )

                mission.nextRun != null ->
                    Text(
                        stringResource(Res.string.tasks_scheduled_next, mission.nextRun!!),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }

            LastRunLine(mission)

            // FlowRow, not Row: the four labels do not fit a phone's width, and a Row divides the
            // shortfall among them rather than admitting it. The last button was left a handful of
            // pixels and rendered « Delete » as a column of single letters (reported 30/08/2026).
            // Wrapping onto a second line costs a row of height and keeps every action readable —
            // including the destructive one, the worst of the four to leave illegible.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // A mission already running is not started twice: the scheduler refuses it anyway
                // (server-side D-041), and offering the button would make that refusal look like a
                // bug rather than a rule.
                TextButton(onClick = onRun, enabled = !mission.running) {
                    ActionLabel(stringResource(Res.string.tasks_scheduled_run))
                }
                TextButton(onClick = onToggle) {
                    ActionLabel(
                        stringResource(
                            if (mission.enabled) {
                                Res.string.tasks_scheduled_disable
                            } else {
                                Res.string.tasks_scheduled_enable
                            },
                        ),
                    )
                }
                TextButton(onClick = { editing = true }) {
                    ActionLabel(stringResource(Res.string.tasks_scheduled_edit))
                }
                TextButton(
                    onClick = { confirmingDelete = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    ActionLabel(stringResource(Res.string.tasks_scheduled_delete))
                }
            }
        }
    }

    if (editing) {
        RescheduleSheet(
            mission = mission,
            onDismiss = { editing = false },
            onSave = { cron, runAt ->
                editing = false
                onReschedule(cron, runAt)
            },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(Res.string.tasks_scheduled_delete_title, mission.name)) },
            // Ce que la suppression emporte, et ce qu'elle n'emporte pas : l'historique reste, et
            // c'est la seule question qu'on se pose avant de confirmer.
            text = { Text(stringResource(Res.string.tasks_scheduled_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(Res.string.tasks_scheduled_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(Res.string.tasks_cancel))
                }
            },
        )
    }
}

/**
 * Changer quand une mission part — l'horaire, ou la date unique.
 *
 * Seul le champ modifié voyage : `modifier` fusionne côté serveur. Renvoyer la mission entière
 * n'était pas une option — `etat` ne publie ni le prompt ni la liste d'outils, donc un écran qui
 * reconstruirait la mission depuis ce qu'il affiche viderait le prompt au premier report. C'est la
 * panne des connecteurs recopiés, un écran plus loin.
 *
 * Les deux champs s'excluent, comme côté serveur : une mission est récurrente OU ponctuelle.
 */
@Composable
private fun RescheduleSheet(
    mission: ScheduledMission,
    onDismiss: () -> Unit,
    onSave: (cron: String?, runAt: String?) -> Unit,
) {
    var recurring by rememberSaveable { mutableStateOf(mission.runAt == null) }
    var cron by rememberSaveable { mutableStateOf(mission.cron.orEmpty()) }
    var runAt by rememberSaveable { mutableStateOf(mission.runAt.orEmpty()) }

    TasksBottomSheet(
        onDismiss = onDismiss,
        horizontalPadding = 24.dp,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(mission.name, style = MaterialTheme.typography.titleLarge)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = recurring,
                onClick = { recurring = true },
                label = { Text(stringResource(Res.string.tasks_scheduled_recurring)) },
            )
            FilterChip(
                selected = !recurring,
                onClick = { recurring = false },
                label = { Text(stringResource(Res.string.tasks_scheduled_once)) },
            )
        }

        if (recurring) {
            OutlinedTextField(
                value = cron,
                onValueChange = { cron = it },
                label = { Text(stringResource(Res.string.tasks_scheduled_cron)) },
                supportingText = { Text(stringResource(Res.string.tasks_scheduled_cron_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = runAt,
                onValueChange = { runAt = it },
                label = { Text(stringResource(Res.string.tasks_scheduled_runat)) },
                supportingText = { Text(stringResource(Res.string.tasks_scheduled_runat_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Le fuseau de la mission, pas celui du téléphone : c'est dans celui-là que le serveur
        // lira l'heure saisie, et les deux diffèrent en voyage.
        if (mission.timeZone.isNotBlank()) {
            Text(
                stringResource(Res.string.tasks_scheduled_timezone, mission.timeZone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.tasks_cancel)) }
            TextButton(
                enabled = if (recurring) cron.isNotBlank() else runAt.isNotBlank(),
                onClick = {
                    if (recurring) onSave(cron.trim(), null) else onSave(null, runAt.trim())
                },
            ) { Text(stringResource(Res.string.tasks_scheduled_save)) }
        }
    }
}

/**
 * One action's caption. Single line and unwrappable on purpose: a caption that wraps inside a button
 * is the symptom of a row that does not fit, and letting it wrap hides the overflow instead of
 * letting the layout resolve it.
 */
@Composable
private fun ActionLabel(text: String) {
    Text(text, maxLines = 1, softWrap = false)
}

/**
 * How the last run went, in one line.
 *
 * `succeeded` is null *while the mission runs*, and that third state is why this is not a boolean:
 * showing a red failure on a mission that is working would be worse than showing nothing.
 */
@Composable
private fun LastRunLine(mission: ScheduledMission) {
    val last = mission.lastRun
    when {
        last == null -> Text(
            stringResource(Res.string.tasks_scheduled_never),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        last.succeeded == false -> Text(
            stringResource(
                Res.string.tasks_scheduled_last_failed,
                last.stopReason.orEmpty(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        last.succeeded == true -> Text(
            stringResource(
                Res.string.tasks_scheduled_last_ok,
                last.startedAt.orEmpty(),
                (last.tokens ?: 0).toString(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MissionRow(
    mission: Mission,
    onOpenChat: () -> Unit,
    onStop: () -> Unit,
) {
    // The whole row is the target and it opens the CONVERSATION — the Cowork-app gesture the user
    // asked to match (29/08). The inline transcript peek this replaced is strictly contained in the
    // chat, which replays the session's whole history before tailing it live.
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenChat)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    mission.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                mission.createdAtMillis?.let { created ->
                    Text(
                        missionAge(created),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MissionChip(mission.state)
                if (mission.state is MissionState.Running) {
                    TextButton(onClick = onStop) { Text(stringResource(Res.string.tasks_stop)) }
                }
            }
            // A failure says why, in the row, without asking anyone to open anything. The reason is
            // the engine's own — flattened, because it arrives as JSON nested three deep.
            (mission.state as? MissionState.Failed)?.let { failed ->
                Text(
                    failed.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MissionChip(state: MissionState) {
    val (label, colour) = when (state) {
        is MissionState.Running ->
            stringResource(Res.string.tasks_state_running) to MaterialTheme.colorScheme.primary
        is MissionState.Succeeded ->
            stringResource(Res.string.tasks_state_succeeded, state.tokens.toString()) to
                MaterialTheme.colorScheme.secondary
        is MissionState.Failed ->
            stringResource(Res.string.tasks_state_failed) to MaterialTheme.colorScheme.error
        MissionState.Idle ->
            stringResource(Res.string.tasks_state_idle) to MaterialTheme.colorScheme.outline
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(labelColor = colour),
    )
}
