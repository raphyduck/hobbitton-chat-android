package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * One recent task, as a row of a group (lot 4, 10/10/2026): a dot for its state, the title, one
 * quiet line under it, the age on the right, and « Arrêter » only while it runs.
 *
 * The whole row opens the CONVERSATION — the Cowork-app gesture asked for on 29/08. The card and
 * the chip it replaced said the same things in three times the height.
 */
@Composable
internal fun MissionRow(
    mission: Mission,
    onOpenChat: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = mission.state is MissionState.Running
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenChat)
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = if (running) 4.dp else 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusDot(color = mission.state.colour(), label = mission.state.label())
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    mission.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
            Text(
                mission.state.detail(),
                style = MaterialTheme.typography.bodySmall,
                color = mission.state.colour(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (running) {
            TextButton(onClick = onStop, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(stringResource(Res.string.tasks_stop))
            }
        }
    }
}

/**
 * A 10 dp dot in the state's colour. Announced by its label so the colour is not the only carrier.
 */
@Composable
internal fun StatusDot(color: Color, label: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(10.dp)
            .background(color, CircleShape)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun MissionState.label(): String = when (this) {
    is MissionState.Running -> stringResource(Res.string.tasks_state_running)
    is MissionState.Succeeded -> stringResource(Res.string.tasks_state_succeeded, groupThousands(tokens))
    is MissionState.Failed -> stringResource(Res.string.tasks_state_failed)
}

/** The second line: the state, and for a failure the engine's own reason after it. */
@Composable
private fun MissionState.detail(): String = when (this) {
    // Flattened by the repository, because the reason arrives as JSON nested three deep.
    is MissionState.Failed -> listOf(label(), reason.trim()).filter { it.isNotEmpty() }.joinToString(" · ")
    else -> label()
}

@Composable
private fun MissionState.colour(): Color = when (this) {
    is MissionState.Running -> MaterialTheme.colorScheme.primary
    is MissionState.Succeeded -> MaterialTheme.colorScheme.onSurfaceVariant
    is MissionState.Failed -> MaterialTheme.colorScheme.error
}
