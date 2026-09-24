package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.garfiec.librechat.feature.tasks.components.Explanation
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.runs_empty
import com.garfiec.librechat.feature.tasks.resources.runs_empty_hint
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_back
import com.garfiec.librechat.feature.tasks.resources.tasks_retry
import com.garfiec.librechat.feature.tasks.util.hint
import com.garfiec.librechat.feature.tasks.util.title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A scheduled mission's runs, newest first — what its card on the Tasks tab opens.
 *
 * Each row is the same one the tab uses for running sessions, and opens the run's conversation the
 * same way: a run is a chat that happened without anyone watching.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionRunsScreen(
    name: String,
    onOpenMissionChat: (sessionId: String, title: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MissionRunsViewModel = koinViewModel { parametersOf(name) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failure = state.error
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(name) },
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
            isRefreshing = state.loading && state.runs.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading && state.runs.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                failure != null && state.runs.isEmpty() -> Explanation(
                    title = stringResource(failure.title()),
                    hint = failure.hint()?.let { stringResource(it) },
                    action = stringResource(Res.string.tasks_retry) to viewModel::refresh,
                )

                state.runs.isEmpty() -> Explanation(
                    title = stringResource(Res.string.runs_empty),
                    hint = stringResource(Res.string.runs_empty_hint),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.runs, key = { it.sessionId }) { run ->
                        MissionRow(
                            mission = run,
                            onOpenChat = { onOpenMissionChat(run.sessionId, run.title) },
                            onStop = { viewModel.abort(run.sessionId) },
                        )
                    }
                }
            }
        }
    }
}
