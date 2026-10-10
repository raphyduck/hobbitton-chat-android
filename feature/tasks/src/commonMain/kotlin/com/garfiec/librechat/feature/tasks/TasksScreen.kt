package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.engine.Mission
import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.model.scheduler.ScheduledMission
import com.garfiec.librechat.core.ui.components.SectionDivider
import com.garfiec.librechat.core.ui.components.SectionGroup
import com.garfiec.librechat.feature.tasks.components.DisclosureRow
import com.garfiec.librechat.feature.tasks.components.Explanation
import com.garfiec.librechat.feature.tasks.components.PortalSignInDialog
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_empty
import com.garfiec.librechat.feature.tasks.resources.tasks_empty_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_new
import com.garfiec.librechat.feature.tasks.resources.tasks_not_configured
import com.garfiec.librechat.feature.tasks.resources.tasks_not_configured_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_open_drawer
import com.garfiec.librechat.feature.tasks.resources.tasks_recent_header
import com.garfiec.librechat.feature.tasks.resources.tasks_retry
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_header
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_once_header
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_recurring_header
import com.garfiec.librechat.feature.tasks.resources.tasks_settings_open
import com.garfiec.librechat.feature.tasks.resources.tasks_sign_in
import com.garfiec.librechat.feature.tasks.resources.tasks_title
import com.garfiec.librechat.feature.tasks.util.hint
import com.garfiec.librechat.feature.tasks.util.sentence
import com.garfiec.librechat.feature.tasks.util.title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Tasks tab: the scheduled missions, and the recent sessions.
 *
 * The spend and the providers moved to Settings › Usage on 24/09/2026. What remains: the schedule,
 * folded and split into recurring and one-shot, then the recent sessions — each mission's own runs
 * are one tap further, on its row. Since lot 4 (10/10/2026) both are rows on grouped cards rather
 * than cards of their own, and the assistant's address sheet is no longer in the top bar: the
 * addresses are the sign-in's, and the sheet is offered only where the tab cannot work without it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    modifier: Modifier = Modifier,
    onOpenMissionChat: (sessionId: String, title: String) -> Unit = { _, _ -> },
    /** A scheduled mission's row opens the list of its runs. */
    onOpenMissionRuns: (name: String) -> Unit = {},
    /**
     * « New task » opens a blank task conversation (02/10/2026): the same screen and composer as an
     * existing task — photos, videos, files, dictation, model and connectors — instead of a form.
     */
    onNewTask: () -> Unit = {},
    /**
     * The menu button, as on the chat. Until 23/09/2026 this bar had no leading button at all: the
     * screen is reached from the drawer, and the only way back to it was the system back gesture.
     */
    onOpenDrawer: (() -> Unit)? = null,
    viewModel: TasksViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var configuring by remember { mutableStateOf(false) }
    // Folded by default, and saved across rotation: whether the schedule is open is the reader's
    // choice, and a rotation must not undo it.
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
                navigationIcon = {
                    onOpenDrawer?.let { open ->
                        IconButton(onClick = open) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(Res.string.tasks_open_drawer))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.engineConfigured) {
                NewTaskButton(onClick = onNewTask)
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

                failure != null && nothingToShow -> Explanation(
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
                            stringResource(Res.string.tasks_sign_in) to viewModel::signIn
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

                else -> TasksListBody(
                    scheduled = state.scheduled,
                    missions = state.missions,
                    scheduledShown = scheduledShown,
                    onToggleSchedule = { scheduledShown = !scheduledShown },
                    failureTitle = failure?.let { stringResource(it.title()) },
                    actions = TasksListActions(
                        onOpenMission = { onOpenMissionChat(it.sessionId, it.title) },
                        onStopMission = { viewModel.abort(it.sessionId) },
                        onOpenRuns = { onOpenMissionRuns(it.name) },
                        onRunScheduled = { viewModel.runScheduled(it.name) },
                        onSetScheduledEnabled = { mission, enabled -> viewModel.setScheduledEnabled(mission.name, enabled) },
                        onReschedule = { mission, cron, runAt -> viewModel.rescheduleMission(mission.name, cron, runAt) },
                        onDeleteScheduled = { viewModel.deleteScheduled(it.name) },
                    ),
                )
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

    // The portal, in the login's web view rather than the browser (D-076): its session cookie is
    // there, so a lapsed token costs one consent click rather than a password and a second factor.
    state.portalPage?.let { page ->
        PortalSignInDialog(
            page = page,
            onNavigation = viewModel::onPortalNavigation,
            onClose = viewModel::cancelSignIn,
        )
    }
}

/** « Nouvelle tâche », in the accent with a plus: the one thing to do from here. */
@Composable
internal fun NewTaskButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        text = { Text(stringResource(Res.string.tasks_new)) },
        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    )
}

/** What the rows can ask of the screen, gathered so the list body stays a function of its data. */
internal class TasksListActions(
    val onOpenMission: (Mission) -> Unit,
    val onStopMission: (Mission) -> Unit,
    val onOpenRuns: (ScheduledMission) -> Unit,
    val onRunScheduled: (ScheduledMission) -> Unit,
    val onSetScheduledEnabled: (ScheduledMission, Boolean) -> Unit,
    val onReschedule: (ScheduledMission, cron: String?, runAt: String?) -> Unit,
    val onDeleteScheduled: (ScheduledMission) -> Unit,
)

/**
 * The tab's list: the schedule first, folded, then the recent tasks, each group of rows on one
 * card. Apart from the screen so the capture test renders exactly what ships.
 */
@Composable
internal fun TasksListBody(
    scheduled: List<ScheduledMission>,
    missions: List<Mission>,
    scheduledShown: Boolean,
    onToggleSchedule: () -> Unit,
    failureTitle: String?,
    actions: TasksListActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The engine failed while the schedule loaded: the list stays, and one line says what is
        // missing from it rather than a full-screen error hiding the rest.
        if (failureTitle != null) {
            item(key = "engine-failure", contentType = "failure") {
                Text(
                    failureTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // The schedule first, folded (asked for on 25/09/2026): it is what one sets up, not what
        // one comes to read, and a dozen cards above the sessions pushed them off the screen. The
        // count rides on the header so the fold still says what it hides. Recurring and one-shot
        // apart, because they answer different questions — « what runs every day » and « what is
        // still to come ».
        if (scheduled.isNotEmpty()) {
            item(key = "scheduled-header", contentType = "header") {
                DisclosureRow(
                    label = stringResource(Res.string.tasks_scheduled_header),
                    expanded = scheduledShown,
                    onToggle = onToggleSchedule,
                    labelStyle = MaterialTheme.typography.titleSmall,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    trailing = {
                        Text(
                            scheduled.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            if (scheduledShown) {
                val (recurring, oneShot) = scheduled.partition { it.runAt == null }
                listOf(
                    Triple("recurring", Res.string.tasks_scheduled_recurring_header, recurring),
                    Triple("once", Res.string.tasks_scheduled_once_header, oneShot),
                ).forEach { (groupKey, header, group) ->
                    if (group.isEmpty()) return@forEach
                    item(key = "scheduled-group-$groupKey", contentType = "subheader") {
                        SubsectionHeader(stringResource(header, group.size))
                    }
                    item(key = "scheduled-rows-$groupKey", contentType = "scheduled") {
                        SectionGroup {
                            group.forEachIndexed { index, mission ->
                                if (index > 0) SectionDivider()
                                ScheduledMissionRow(
                                    mission = mission,
                                    onOpen = { actions.onOpenRuns(mission) },
                                    onRun = { actions.onRunScheduled(mission) },
                                    onToggle = { actions.onSetScheduledEnabled(mission, !mission.enabled) },
                                    onReschedule = { cron, runAt -> actions.onReschedule(mission, cron, runAt) },
                                    onDelete = { actions.onDeleteScheduled(mission) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Then the recent sessions, running or settled, newest first — a running one carries its
        // Stop on the row.
        if (missions.isNotEmpty()) {
            item(key = "recent-header", contentType = "header") {
                SectionHeader(stringResource(Res.string.tasks_recent_header))
            }
            item(key = "recent-rows", contentType = "missions") {
                SectionGroup {
                    missions.forEachIndexed { index, mission ->
                        if (index > 0) SectionDivider()
                        MissionRow(
                            mission = mission,
                            onOpenChat = { actions.onOpenMission(mission) },
                            onStop = { actions.onStopMission(mission) },
                        )
                    }
                }
            }
        }
    }
}

/** « Récurrentes · 9 » — a group inside the folded schedule, quieter than a section. */
@Composable
private fun SubsectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
    )
}

@Composable
internal fun SectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}
