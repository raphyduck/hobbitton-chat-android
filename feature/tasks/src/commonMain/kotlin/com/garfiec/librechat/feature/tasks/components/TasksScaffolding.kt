package com.garfiec.librechat.feature.tasks.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_collapse
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_expand
import org.jetbrains.compose.resources.stringResource

/**
 * The tab's one bottom-sheet shape.
 *
 * Every sheet in the module used to hand-roll the same `ModalBottomSheet` + scrolling `Column` +
 * bottom padding — six copies, and only one of them remembered `imePadding()`. The two sheets that
 * carry text fields (new mission, reschedule) therefore had the keyboard land ON the field being
 * typed into: the exact bug the conversation's composer shipped with on 30/08/2026, rebuilt three
 * screens away. One scaffold, `imePadding()` always on, and the class of bug is gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TasksBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding)
                .padding(bottom = 32.dp)
                .imePadding(),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * A row that folds something away: label, optional leading/trailing slots, and the chevron.
 *
 * One shape for the scheduled-missions header, the spend breakdown and the conversation's activity
 * blocks — three hand-rolled copies before, of which only one labelled its chevron for TalkBack.
 * The 40 dp minimum height is the chat's own (`ActivityGroup`), and it is what makes the row a
 * touch target rather than a line of text that happens to be tappable.
 */
@Composable
internal fun DisclosureRow(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Text(label, style = labelStyle, color = labelColor, modifier = Modifier.weight(1f))
        trailing?.invoke()
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = stringResource(
                if (expanded) Res.string.tasks_chat_collapse else Res.string.tasks_chat_expand,
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * A full-screen sentence about why there is nothing else to show, with up to two ways out.
 *
 * Shared between the tab and the conversation: the two used to disagree by construction — the tab
 * showed a title and a hint, the conversation could only reach the title.
 */
@Composable
internal fun Explanation(
    title: String,
    hint: String?,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
    /** Offered under [action] when there are two ways out and one is clearly the usual one. */
    secondary: Pair<String, () -> Unit>? = null,
    /**
     * Something is under way — the portal round trip, in practice. Both offers are withdrawn while
     * it is: a second tap opens a second browser tab against a request the first one already
     * consumed, and the failure that produces names neither.
     */
    busy: Boolean = false,
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
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        } else {
            action?.let { (label, onClick) ->
                TextButton(onClick = onClick, modifier = Modifier.padding(top = 16.dp)) { Text(label) }
            }
            secondary?.let { (label, onClick) ->
                TextButton(onClick = onClick) { Text(label) }
            }
        }
    }
}
