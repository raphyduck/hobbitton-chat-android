package com.garfiec.librechat.feature.tasks.components

import androidx.compose.runtime.Composable
import com.garfiec.librechat.feature.tasks.util.ChatPart

/**
 * Opens a file of the conversation with the phone's own viewer (19/10/2026), from which it can be
 * saved or shared: an invoice the agent fetched was a name one could not open. Null where there is
 * no viewer to hand it to.
 */
@Composable
internal expect fun rememberAttachmentOpener(): ((ChatPart.Attachment) -> Unit)?
