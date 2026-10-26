package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.model.scheduler.ScheduledMission
import com.garfiec.librechat.core.ui.components.SectionDivider
import com.garfiec.librechat.core.ui.components.SectionGroup
import com.garfiec.librechat.feature.tasks.components.Explanation
import com.garfiec.librechat.feature.tasks.components.PortalSignInDialog
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_empty
import com.garfiec.librechat.feature.tasks.resources.tasks_empty_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_open_drawer
import com.garfiec.librechat.feature.tasks.resources.tasks_retry
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_intro
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_once_header
import com.garfiec.librechat.feature.tasks.resources.tasks_scheduled_recurring_header
import com.garfiec.librechat.feature.tasks.resources.tasks_sign_in
import com.garfiec.librechat.feature.tasks.resources.tasks_title
import com.garfiec.librechat.feature.tasks.util.hint
import com.garfiec.librechat.feature.tasks.util.sentence
import com.garfiec.librechat.feature.tasks.util.title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Programmées (ex Tasks tab): the scheduler's entries, and nothing else.
 *
 * Since D-082 (12/10/2026), as on Claude.ai: a mission's runs are conversations, listed in the
 * drawer with every other session, and this screen holds what the scheduler will do: recurring
 * missions, then one-shot ones, unfolded, each row opening its actions (run now, suspend,
 * reschedule, delete). No « New task » any more: any conversation does what a task did. The spend
 * and the providers moved to Settings › Usage on 24/09/2026; the assistant's address sheet is
 * offered only where the screen cannot work without it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    modifier: Modifier = Modifier,
    /**
     * The menu button, as on a conversation. Until 23/09/2026 this bar had no leading button at all:
     * the screen is reached from the drawer, and the only way back to it was the system back gesture.
     */
    onOpenDrawer: (() -> Unit)? = null,
    viewModel: TasksViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read once into a local: `state` is a delegated property, so the branch below cannot smart-cast
    // through it.
    val failure = state.error
    // A spinner owns the screen only while there is genuinely nothing to show. Past that the pull
    // indicator carries the refresh.
    val nothingToShow = state.scheduled.isEmpty()

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
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && !nothingToShow,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
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
                    // suspecting their settings. The address sheet this screen used to open went
                    // with the review (26/10/2026): the addresses are the sign-in's since D-077.
                    action = when (failure) {
                        // Going through the portal is the remedy, and it was missing entirely until
                        // 24 August: the tab offered the settings form, so the only thing anyone could
                        // do about a missing token was retype a password that had nothing to do with it.
                        EngineFailureKind.AUTHENTICATION ->
                            stringResource(Res.string.tasks_sign_in) to viewModel::signIn
                        EngineFailureKind.PERMISSION -> null
                        else -> stringResource(Res.string.tasks_retry) to viewModel::refresh
                    },
                    busy = state.signingIn,
                )

                nothingToShow -> Explanation(
                    title = stringResource(Res.string.tasks_empty),
                    hint = stringResource(Res.string.tasks_empty_hint),
                )

                else -> TasksListBody(
                    scheduled = state.scheduled,
                    failureTitle = failure?.let { stringResource(it.title()) },
                    actions = TasksListActions(
                        onRunScheduled = { viewModel.runScheduled(it.name) },
                        onSetScheduledEnabled = { mission, enabled -> viewModel.setScheduledEnabled(mission.name, enabled) },
                        onReschedule = { mission, cron, runAt -> viewModel.rescheduleMission(mission.name, cron, runAt) },
                        onDeleteScheduled = { viewModel.deleteScheduled(it.name) },
                        onSetDiscreet = { mission, discreet -> viewModel.setScheduledDiscreet(mission.name, discreet) },
                    ),
                )
            }
        }
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

/** What the rows can ask of the screen, gathered so the list body stays a function of its data. */
internal class TasksListActions(
    val onRunScheduled: (ScheduledMission) -> Unit,
    val onSetScheduledEnabled: (ScheduledMission, Boolean) -> Unit,
    val onReschedule: (ScheduledMission, cron: String?, runAt: String?) -> Unit,
    val onDeleteScheduled: (ScheduledMission) -> Unit,
    val onSetDiscreet: (ScheduledMission, Boolean) -> Unit,
)

/**
 * The list: one line on what the page holds, then the recurring missions and the one-shot ones,
 * each group of rows on one card. Recurring and one-shot apart, because they answer different
 * questions: « what runs every day » and « what is still to come ». Apart from the screen so the
 * capture test renders exactly what ships.
 */
@Composable
internal fun TasksListBody(
    scheduled: List<ScheduledMission>,
    failureTitle: String?,
    actions: TasksListActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "intro", contentType = "intro") {
            Text(
                stringResource(Res.string.tasks_scheduled_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        // The scheduler failed after a first load: the list stays, and one line says so rather
        // than a full-screen error hiding it.
        if (failureTitle != null) {
            item(key = "failure", contentType = "failure") {
                Text(
                    failureTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
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
                            onRun = { actions.onRunScheduled(mission) },
                            onToggle = { actions.onSetScheduledEnabled(mission, !mission.enabled) },
                            onReschedule = { cron, runAt -> actions.onReschedule(mission, cron, runAt) },
                            onDelete = { actions.onDeleteScheduled(mission) },
                            onToggleDiscreet = { actions.onSetDiscreet(mission, !mission.discreet) },
                        )
                    }
                }
            }
        }
    }
}

/** « Récurrentes · 9 »: the name of a group of rows. */
@Composable
private fun SubsectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
    )
}
