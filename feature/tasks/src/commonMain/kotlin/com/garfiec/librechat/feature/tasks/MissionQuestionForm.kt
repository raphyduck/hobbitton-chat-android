package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.engine.EngineFailureKind
import com.garfiec.librechat.core.model.engine.EngineQuestionInfo
import com.garfiec.librechat.core.model.engine.EngineQuestionRequest
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_question_dismiss
import com.garfiec.librechat.feature.tasks.resources.tasks_question_other
import com.garfiec.librechat.feature.tasks.resources.tasks_question_pick_several
import com.garfiec.librechat.feature.tasks.resources.tasks_question_send
import com.garfiec.librechat.feature.tasks.resources.tasks_question_title
import com.garfiec.librechat.feature.tasks.resources.tasks_question_waiting
import com.garfiec.librechat.feature.tasks.util.QuestionDraft
import com.garfiec.librechat.feature.tasks.util.title
import org.jetbrains.compose.resources.stringResource

/**
 * The agent's question, as a form in place of the composer, what Claude's own app shows when it
 * asks (03/10/2026). The turn is blocked until this is answered or dismissed, so the form takes the
 * composer's place rather than sitting beside it: a message typed now would only queue behind the
 * question it did not answer.
 *
 * Each question of the request is shown in turn, on one scroll: its header, its sentence, its
 * options (radio buttons, or checkboxes when several may be picked) with their explanation, then
 * (when the model allows it, which is the engine's default) a free field for an answer of one's own.
 * « Send » waits until every question has an answer; « Dismiss » lets the agent go on without one.
 */
@Composable
internal fun MissionQuestionForm(
    request: EngineQuestionRequest,
    draft: QuestionDraft,
    sending: Boolean,
    error: EngineFailureKind?,
    onPick: (index: Int, label: String) -> Unit,
    onType: (index: Int, text: String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The composer's own rule: the keyboard or the navigation bar, whichever is taller.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("mission_question_form"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.QuestionAnswer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.tasks_question_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Room for the transcript above: a long request scrolls inside the form.
                    .heightIn(max = FORM_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                request.questions.forEachIndexed { index, question ->
                    if (index > 0) HorizontalDivider()
                    QuestionBlock(
                        question = question,
                        picked = draft.picked.getOrNull(index).orEmpty(),
                        typed = draft.typed.getOrNull(index).orEmpty(),
                        enabled = !sending,
                        onPick = { label -> onPick(index, label) },
                        onType = { text -> onType(index, text) },
                    )
                }
            }
            if (error != null) {
                Text(
                    text = stringResource(error.title()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.tasks_question_waiting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !sending,
                    modifier = Modifier.testTag("mission_question_dismiss"),
                ) {
                    Text(stringResource(Res.string.tasks_question_dismiss))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSend,
                    enabled = !sending && draft.isComplete(),
                    modifier = Modifier.testTag("mission_question_send"),
                ) {
                    Text(stringResource(Res.string.tasks_question_send))
                }
            }
        }
    }
}

@Composable
private fun QuestionBlock(
    question: EngineQuestionInfo,
    picked: List<String>,
    typed: String,
    enabled: Boolean,
    onPick: (String) -> Unit,
    onType: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (question.header.isNotBlank()) {
            Text(
                text = question.header,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = question.question, style = MaterialTheme.typography.bodyLarge)
        if (question.multiple) {
            Text(
                text = stringResource(Res.string.tasks_question_pick_several),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        question.options.forEach { option ->
            val selected = option.label in picked
            val selection = if (question.multiple) {
                Modifier.toggleable(
                    value = selected,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = { onPick(option.label) },
                )
            } else {
                Modifier.selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = { onPick(option.label) },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(selection)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (question.multiple) {
                    Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
                } else {
                    RadioButton(selected = selected, onClick = null, enabled = enabled)
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(text = option.label, style = MaterialTheme.typography.bodyMedium)
                    if (option.description.isNotBlank()) {
                        Text(
                            text = option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // The engine's default, and the model is told so: it never offers an « Other » of its own.
        if (question.custom || question.options.isEmpty()) {
            OutlinedTextField(
                value = typed,
                onValueChange = onType,
                enabled = enabled,
                placeholder = { Text(stringResource(Res.string.tasks_question_other)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
        }
    }
}

private val FORM_MAX_HEIGHT = 360.dp
