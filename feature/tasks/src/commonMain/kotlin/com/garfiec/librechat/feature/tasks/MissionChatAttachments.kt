package com.garfiec.librechat.feature.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.garfiec.librechat.feature.tasks.resources.Res
import com.garfiec.librechat.feature.tasks.resources.tasks_attached_photo
import com.garfiec.librechat.feature.tasks.resources.tasks_attachment_remove
import com.garfiec.librechat.feature.tasks.util.AudioNote
import com.garfiec.librechat.feature.tasks.util.ChatPart
import com.garfiec.librechat.feature.tasks.util.StagedAttachment
import org.jetbrains.compose.resources.stringResource

/**
 * One attachment in the transcript. An image draws as the picture — bounded, clipped like a bubble;
 * anything else names itself, because rendering raw base64 helps nobody.
 */
@Composable
internal fun AttachmentContent(part: ChatPart.Attachment) {
    if (part.mime.startsWith("image/")) {
        AsyncImage(
            model = part.dataUrl,
            contentDescription = part.filename ?: stringResource(Res.string.tasks_attached_photo),
            modifier = Modifier
                .heightIn(max = ATTACHMENT_MAX_HEIGHT)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Text(
            part.filename ?: part.mime,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * What is staged for the next message: photo thumbnails, and one chip per transcribed audio —
 * each with its remove cross. The chip names the file, because that name is what the thread will
 * quote above the words.
 */
@Composable
internal fun StagedAttachmentsRow(
    attachments: List<StagedAttachment>,
    audioNotes: List<AudioNote>,
    onRemoveAttachment: (String) -> Unit,
    onRemoveAudioNote: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEach { staged ->
            Box {
                AsyncImage(
                    model = staged.bytes,
                    contentDescription = staged.filename,
                    modifier = Modifier
                        .size(STAGED_THUMBNAIL_SIZE)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                IconButton(
                    onClick = { onRemoveAttachment(staged.id) },
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.tasks_attachment_remove),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        audioNotes.forEach { note ->
            AssistChip(
                onClick = { onRemoveAudioNote(note.id) },
                leadingIcon = { Icon(Icons.Outlined.AudioFile, null, Modifier.size(16.dp)) },
                label = { Text(note.filename) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.tasks_attachment_remove),
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

private val ATTACHMENT_MAX_HEIGHT = 280.dp
private val STAGED_THUMBNAIL_SIZE = 72.dp
