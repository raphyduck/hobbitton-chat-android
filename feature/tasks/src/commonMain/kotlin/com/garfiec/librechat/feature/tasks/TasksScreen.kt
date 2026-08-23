package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.engine.Mission
import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.model.engine.MissionState
import com.garfiec.librechat.core.model.scheduler.ScheduledMission
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_empty
import com.garfiec.librechat.feature.tasks.resources.tasks_empty_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_error_authentication
import com.garfiec.librechat.feature.tasks.resources.tasks_error_authentication_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_error_not_found
import com.garfiec.librechat.feature.tasks.resources.tasks_error_not_found_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_error_permission
import com.garfiec.librechat.feature.tasks.resources.tasks_error_permission_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_error_server
import com.garfiec.librechat.feature.tasks.resources.tasks_error_server_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_error_unknown
import com.garfiec.librechat.feature.tasks.resources.tasks_error_unreachable
import com.garfiec.librechat.feature.tasks.resources.tasks_error_unreachable_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_new
import com.garfiec.librechat.feature.tasks.resources.tasks_not_configured
import com.garfiec.librechat.feature.tasks.resources.tasks_not_configured_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_retry
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_disable
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_enable
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_header
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_last_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_last_ok
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_never
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_next
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_run
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_suspended
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_tools
import com.garfiec.librechat.feature.tasks.resources.tasks_sessions_header
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_open
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_title
import com.garfiec.librechat.feature.tasks.resources.tasks_state_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_state_idle
import com.garfiec.librechat.feature.tasks.resources.tasks_state_running
import com.garfiec.librechat.feature.tasks.resources.tasks_state_succeeded
import com.garfiec.librechat.feature.tasks.resources.tasks_stop
import com.garfiec.librechat.feature.tasks.resources.tasks_title
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
    viewModel: TasksViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var composing by remember { mutableStateOf(false) }
    var configuring by remember { mutableStateOf(false) }
    // Read once into a local: `state` is a delegated property, so the branch below cannot smart-cast
    // through it.
    val failure = state.error

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
        when {
            // « Not set up » is not « broken ». Offering a retry here would send someone to check
            // their network over a settings form they have never filled in.
            !state.engineConfigured -> Explanation(
                title = stringResource(Res.string.tasks_not_configured),
                hint = stringResource(Res.string.tasks_not_configured_hint),
                action = stringResource(Res.string.tasks_settings_open) to { configuring = true },
                modifier = Modifier.padding(padding),
            )

            state.loading && state.missions.isEmpty() ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }

            failure != null && state.missions.isEmpty() -> Explanation(
                title = stringResource(failure.title()),
                hint = failure.hint()?.let { stringResource(it) },
                // The offer follows the cause. « Retry » in front of an expired session is a button
                // that cannot work, and it is the one someone will press five times before
                // suspecting their settings.
                action = when (failure) {
                    EngineFailureKind.AUTHENTICATION, EngineFailureKind.NOT_FOUND ->
                        stringResource(Res.string.tasks_settings_open) to { configuring = true }
                    EngineFailureKind.PERMISSION -> null
                    else -> stringResource(Res.string.tasks_retry) to viewModel::refresh
                },
                modifier = Modifier.padding(padding),
            )

            // « No session yet » is not « nothing to show »: the nine missions that run every
            // night are still there, and hiding them behind an empty sessions list was exactly
            // what made all of that work invisible from a phone.
            state.missions.isEmpty() && state.scheduled.isEmpty() -> Explanation(
                title = stringResource(Res.string.tasks_empty),
                hint = stringResource(Res.string.tasks_empty_hint),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The schedule leads: what runs tonight without anyone watching is what someone
                // opens this tab to check. Sessions are the record of what already happened.
                if (state.scheduled.isNotEmpty()) {
                    item(key = "scheduled-header") {
                        SectionHeader(stringResource(Res.string.tasks_scheduled_header))
                    }
                    items(state.scheduled, key = { "scheduled-" + it.name }) { mission ->
                        ScheduledMissionRow(
                            mission = mission,
                            onRun = { viewModel.runScheduled(mission.name) },
                            onToggle = {
                                viewModel.setScheduledEnabled(mission.name, !mission.enabled)
                            },
                        )
                    }
                }
                if (state.missions.isNotEmpty()) {
                    item(key = "sessions-header") {
                        SectionHeader(stringResource(Res.string.tasks_sessions_header))
                    }
                    items(state.missions, key = { it.sessionId }) { mission ->
                        MissionRow(mission = mission, onStop = { viewModel.abort(mission.sessionId) })
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
        NewMissionSheet(
            profiles = state.profiles,
            onDismiss = { composing = false },
            onLaunch = { profile, objective, connectors, autonomous ->
                composing = false
                viewModel.launch(profile, objective, connectors, autonomous)
            },
        )
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
@Composable
private fun ScheduledMissionRow(
    mission: ScheduledMission,
    onRun: () -> Unit,
    onToggle: () -> Unit,
) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // A mission already running is not started twice: the scheduler refuses it anyway
                // (server-side D-041), and offering the button would make that refusal look like a
                // bug rather than a rule.
                TextButton(onClick = onRun, enabled = !mission.running) {
                    Text(stringResource(Res.string.tasks_scheduled_run))
                }
                TextButton(onClick = onToggle) {
                    Text(
                        stringResource(
                            if (mission.enabled) {
                                Res.string.tasks_scheduled_disable
                            } else {
                                Res.string.tasks_scheduled_enable
                            },
                        ),
                    )
                }
            }
        }
    }
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
private fun MissionRow(mission: Mission, onStop: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(mission.title, style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun Explanation(
    title: String,
    hint: String?,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        action?.let { (label, onClick) ->
            TextButton(onClick = onClick, modifier = Modifier.padding(top = 16.dp)) { Text(label) }
        }
    }
}

/** The sentence shown for a failure. One per cause, because the remedies differ. */
private fun EngineFailureKind.title() = when (this) {
    EngineFailureKind.AUTHENTICATION -> Res.string.tasks_error_authentication
    EngineFailureKind.PERMISSION -> Res.string.tasks_error_permission
    EngineFailureKind.NOT_FOUND -> Res.string.tasks_error_not_found
    EngineFailureKind.UNREACHABLE -> Res.string.tasks_error_unreachable
    EngineFailureKind.SERVER -> Res.string.tasks_error_server
    EngineFailureKind.UNKNOWN -> Res.string.tasks_error_unknown
}

private fun EngineFailureKind.hint() = when (this) {
    EngineFailureKind.AUTHENTICATION -> Res.string.tasks_error_authentication_hint
    EngineFailureKind.PERMISSION -> Res.string.tasks_error_permission_hint
    EngineFailureKind.NOT_FOUND -> Res.string.tasks_error_not_found_hint
    EngineFailureKind.UNREACHABLE -> Res.string.tasks_error_unreachable_hint
    EngineFailureKind.SERVER -> Res.string.tasks_error_server_hint
    EngineFailureKind.UNKNOWN -> null
}
