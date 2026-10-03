package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.engine.Mission
import com.garfiec.librechat.core.model.engine.MissionState
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_state_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_state_running
import com.garfiec.librechat.feature.tasks.resources.tasks_state_succeeded
import com.garfiec.librechat.feature.tasks.resources.tasks_stop
import com.garfiec.librechat.feature.tasks.util.groupThousands
import com.garfiec.librechat.feature.tasks.util.missionAge
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MissionRow(
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
                mission.lastActivityMillis?.let { lastActivity ->
                    Text(
                        missionAge(lastActivity),
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
            stringResource(Res.string.tasks_state_succeeded, groupThousands(state.tokens)) to
                MaterialTheme.colorScheme.secondary
        is MissionState.Failed ->
            stringResource(Res.string.tasks_state_failed) to MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        // A running task shows the spinner the drawer and the tool rows show beside the same word.
        leadingIcon = if (state is MissionState.Running) {
            {
                CircularProgressIndicator(
                    Modifier.size(AssistChipDefaults.IconSize),
                    color = colour,
                    strokeWidth = 2.dp,
                )
            }
        } else {
            null
        },
        colors = AssistChipDefaults.assistChipColors(labelColor = colour),
    )
}
