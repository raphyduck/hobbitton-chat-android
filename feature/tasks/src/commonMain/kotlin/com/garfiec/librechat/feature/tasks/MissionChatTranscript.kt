package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.markdown.StreamingWaitIndicator
import com.garfiec.librechat.core.ui.util.copyToClipboard
import com.garfiec.librechat.feature.tasks.components.MissionMarkdown
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_chat_copy
import com.garfiec.librechat.feature.tasks.resources.tasks_copied
import com.garfiec.librechat.feature.tasks.util.ChatBlock
import com.garfiec.librechat.feature.tasks.util.ChatPart
import com.garfiec.librechat.feature.tasks.util.ChatTurn
import com.garfiec.librechat.feature.tasks.util.asBlocks
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun UserBubble(turn: ChatTurn.User, fontScale: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            // The chat's own bubble: secondaryContainer, chosen there over primaryContainer for
            // dark-mode contrast — same reason, same colour here. Shape and the 12 dp inner
            // padding are the chat's too (`BubbleShape`, `MessageBubble`).
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(16.dp),
            // Wraps its content instead of always claiming a fixed fraction: « ok » used to ship
            // in a bubble 85 % of the screen wide.
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                turn.parts.forEach { part ->
                    when (part) {
                        is ChatPart.Attachment -> AttachmentContent(part)
                        is ChatPart.Text ->
                            if (part.text.isNotBlank()) {
                                MissionMarkdown(
                                    part.text,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontScale = fontScale,
                                )
                            }
                        else -> Unit
                    }
                }
            }
        }
    }
}

/**
 * The assistant's turn: the answer at full width, the work that produced it folded away.
 *
 * Flat rather than in a bubble — the chat does the same: a long answer inside a coloured box is
 * harder to read than one that owns the width.
 *
 * Reasoning and tool calls arrive folded, like the chat's activity blocks. A mission's turn is
 * mostly process — nine tool calls and a paragraph of thinking around two sentences — and shipping
 * it flat on 30/08/2026 buried the part anyone actually reads.
 */
@Composable
internal fun AssistantTurn(turn: ChatTurn.Assistant, streaming: Boolean, fontScale: Float) {
    val blocks = remember(turn.parts) { turn.parts.asBlocks() }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The cursor belongs to the LAST prose block, not to the last block: a turn that ends on a
        // folded activity has nowhere visible to put an insertion point, and the answer above it is
        // still where the next character lands.
        val lastProse = blocks.indexOfLast { it is ChatBlock.Prose }
        blocks.forEachIndexed { index, block ->
            when (block) {
                is ChatBlock.Media -> AttachmentContent(block.part)
                is ChatBlock.Prose -> MissionMarkdown(
                    text = block.part.text,
                    fontScale = fontScale,
                    trailingCursor = streaming && index == lastProse,
                )
                is ChatBlock.Activity -> ActivityBlock(block)
            }
        }
        // Before the first delta there is no insertion point, so the wait indicator stands in for
        // the cursor — the chat's own rule, and its own three dots.
        if (streaming && lastProse < 0) {
            StreamingWaitIndicator()
        }
        // Under a finished answer, the action row the chat and Claude both have. Only « copy » for
        // now: a mission has no regenerate, and its feedback would reach nobody.
        if (!streaming && lastProse >= 0) {
            val prose = blocks.filterIsInstance<ChatBlock.Prose>().joinToString("\n\n") { it.part.text }
            CopyTurnButton(prose)
        }
    }
}

/** Copies an answer's prose — without its folded work — and says so for two seconds. */
@Composable
private fun CopyTurnButton(text: String) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }
    // Outside the turn's selection: a button is not text anyone meant to select.
    DisableSelection {
        IconButton(
            onClick = {
                copyToClipboard(text.trim(), "Mission")
                copied = true
            },
            modifier = Modifier.size(COPY_BUTTON_SIZE),
        ) {
            Icon(
                if (copied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                contentDescription = stringResource(if (copied) Res.string.tasks_copied else Res.string.tasks_chat_copy),
                modifier = Modifier.size(18.dp),
                tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val COPIED_FEEDBACK_MS = 2_000L
private val COPY_BUTTON_SIZE = 36.dp
