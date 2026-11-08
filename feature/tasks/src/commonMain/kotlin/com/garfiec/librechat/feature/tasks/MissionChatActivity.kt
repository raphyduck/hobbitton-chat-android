package com.garfiec.librechat.feature.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.tasks.components.DisclosureRow
import com.garfiec.librechat.feature.tasks.components.RunningIndicator
import com.garfiec.librechat.feature.tasks.components.SignatureCaption
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_argument
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_collapse
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_expand
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_output_truncated
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_reasoning
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_tool_count
import com.garfiec.librechat.feature.tasks.resources.tasks_state_running
import com.garfiec.librechat.feature.tasks.resources.tasks_tool_failed
import com.garfiec.librechat.feature.tasks.resources.tasks_tool_succeeded
import com.garfiec.librechat.feature.tasks.util.ChatBlock
import com.garfiec.librechat.feature.tasks.util.ChatPart
import com.garfiec.librechat.feature.tasks.util.ToolState
import com.garfiec.librechat.feature.tasks.util.hasFailure
import com.garfiec.librechat.feature.tasks.util.hasReasoning
import com.garfiec.librechat.feature.tasks.util.isRunning
import com.garfiec.librechat.feature.tasks.util.toolCount
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The work behind an answer, folded.
 *
 * Two states are **never** hidden by the fold, and they are the reason the header says more than a
 * count: a tool still running (a mission waiting on one looks exactly like a mission that stopped)
 * and a tool that failed (a failure folded away is a failure nobody reads). Both surface on the
 * closed header — spinner and error colour — so folding costs no information one would act on.
 *
 * `rememberSaveable` keyed on the block, so a fold the reader opened survives a recomposition, and
 * a delta appending to the turn does not snap it shut under their thumb.
 */
@Composable
internal fun ActivityBlock(
    block: ChatBlock.Activity,
    taskSignature: (String) -> String? = { null },
    /** Who ran each part (08/11/2026): on the folded header when the block has one author, on each row inside. */
    signatureFor: (ChatPart) -> String? = { null },
) {
    var expanded by rememberSaveable(block.key) { mutableStateOf(false) }
    val failed = block.hasFailure()
    val running = block.isRunning()
    val signatures = block.parts.mapNotNull(signatureFor).distinct()

    Column(Modifier.fillMaxWidth()) {
        // The chat's activity header, measure for measure (`ActivityGroup`): 16 dp Build icon at
        // full onSurfaceVariant, bodyMedium label, 8 dp gaps, a 40 dp touch target. What the chat
        // does NOT have and this keeps: a spinner while a tool still runs and error colour on a
        // failure — the two states a fold must never hide.
        DisclosureRow(
            label = activityLabel(block, taskSignature),
            expanded = expanded,
            onToggle = { expanded = !expanded },
            labelColor = if (failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            // One author for the whole block: said on the header, no need to open it. Several
            // (a step that thought on one model, a tool called by another): each row says its own.
            trailing = signatures.singleOrNull()?.let { signature -> { SignatureCaption(signature) } },
            leading = {
                // Each state is named for TalkBack: colour and a spinner say nothing to it.
                when {
                    running -> {
                        val description = stringResource(Res.string.tasks_state_running)
                        CircularProgressIndicator(
                            Modifier.size(14.dp).semantics { contentDescription = description },
                            strokeWidth = 2.dp,
                        )
                    }
                    failed -> Icon(
                        Icons.Filled.Close,
                        stringResource(Res.string.tasks_tool_failed),
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    // Done: a dot of the accent, named for TalkBack, rather than a wrench. The
                    // work is folded; its marker should not shout louder than the answer.
                    else -> {
                        val description = stringResource(Res.string.tasks_tool_succeeded)
                        Box(
                            Modifier.size(16.dp).semantics { contentDescription = description },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        }
                    }
                }
            },
        )
        // Animated like the chat's: a hard pop reads as the list having jumped, not as a fold.
        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                Modifier.padding(start = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Each row is signed only when the block's rows differ: a signature the header
                // already carries would be repeated on every line.
                val rowSignature: (ChatPart) -> String? = if (signatures.size > 1) signatureFor else { _ -> null }
                block.parts.forEach { part ->
                    when (part) {
                        is ChatPart.Tool -> ToolRow(part, rowSignature(part))
                        // bodyMedium: thinking is read as prose, and at bodySmall in the secondary
                        // colour it fell short of a comfortable contrast on the dark theme.
                        is ChatPart.Reasoning -> ReasoningRow(part, rowSignature(part))
                        // Neither ever reaches an activity group: prose and attachments open
                        // their own blocks in asBlocks.
                        is ChatPart.Text -> Unit
                        is ChatPart.Attachment -> Unit
                        // On a user turn only, drawn as its own notice.
                        is ChatPart.Compaction -> Unit
                    }
                }
            }
        }
    }
}

/** « 3 outils · réflexion » — enough to decide whether opening it is worth it. */
@Composable
private fun activityLabel(block: ChatBlock.Activity, taskSignature: (String) -> String?): String {
    val tools = block.toolCount()
    val parts = buildList {
        if (tools > 0) add(pluralStringResource(Res.plurals.tasks_chat_tool_count, tools, tools))
        if (block.hasReasoning()) add(stringResource(Res.string.tasks_chat_reasoning))
        // A delegation (08/11/2026): the fold's header names who it went to, « → éclaireur ·
        // DeepSeek Flash », since the sub-agent runs on another model than the answer above.
        block.subAgents().mapNotNull(taskSignature).distinct().forEach { add("→ $it") }
    }
    return parts.joinToString(" · ").ifEmpty { stringResource(Res.string.tasks_chat_reasoning) }
}

/** The sub-agents the block's `task` calls name (`eclaireur-mission` → `eclaireur`), in order. */
private fun ChatBlock.Activity.subAgents(): List<String> =
    parts.filterIsInstance<ChatPart.Tool>()
        .filter { it.name == TASK_TOOL }
        .mapNotNull { tool -> tool.arguments.firstOrNull { it.name == SUB_AGENT_ARGUMENT }?.value }
        .map { it.trim('"').removeSuffix(MISSION_SUFFIX) }

private const val TASK_TOOL = "task"
private const val SUB_AGENT_ARGUMENT = "subagent_type"
private const val MISSION_SUFFIX = "-mission"

/**
 * One tool call — its name and outcome, and, when opened, what it was called with and what it
 * answered.
 *
 * The payload is behind a second fold on purpose. A tool's output runs to a measured 51 000
 * characters, so unfolding it with the activity block would bury the answer the block was folded to
 * protect. A call with neither arguments nor output does not open at all: an empty drawer with a
 * chevron on it is a promise the row cannot keep.
 */
/** The model's thinking, and under it who thought, when the block's rows have different authors. */
@Composable
private fun ReasoningRow(part: ChatPart.Reasoning, signature: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = part.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        signature?.let { SignatureCaption(it, Modifier.align(Alignment.End)) }
    }
}

@Composable
private fun ToolRow(tool: ChatPart.Tool, signature: String? = null) {
    val hasPayload = tool.arguments.isNotEmpty() || tool.output != null
    var open by rememberSaveable(tool.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = if (hasPayload) {
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { open = !open }
            } else {
                Modifier.fillMaxWidth()
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Build,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                tool.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            when (tool.state) {
                ToolState.RUNNING -> RunningIndicator()
                ToolState.OK -> Icon(
                    Icons.Filled.Check,
                    stringResource(Res.string.tasks_tool_succeeded),
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                ToolState.FAILED -> Icon(
                    Icons.Filled.Close,
                    stringResource(Res.string.tasks_tool_failed),
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.weight(1f))
            signature?.let { SignatureCaption(it) }
            if (hasPayload) {
                Icon(
                    imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (open) Res.string.tasks_chat_collapse else Res.string.tasks_chat_expand,
                    ),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(visible = open, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier.padding(start = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tool.arguments.forEach { argument ->
                    Text(
                        text = stringResource(Res.string.tasks_chat_argument, argument.name, argument.value),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                tool.output?.let { output ->
                    ToolOutput(output)
                }
            }
        }
    }
}

/**
 * What a tool answered, on a raised surface like a code block — it is machine output, not prose.
 *
 * Capped, and it says so when it caps: the median answer is 760 characters but the measured maximum
 * is 51 425, and a fold that pastes fifty thousand characters into the transcript has un-folded the
 * turn by another route.
 */
@Composable
private fun ToolOutput(output: String) {
    val shown = output.take(TOOL_OUTPUT_LIMIT)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = shown,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (output.length > TOOL_OUTPUT_LIMIT) {
            val hidden = output.length - TOOL_OUTPUT_LIMIT
            Text(
                text = pluralStringResource(Res.plurals.tasks_chat_output_truncated, hidden, hidden),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val TOOL_OUTPUT_LIMIT = 2_000
