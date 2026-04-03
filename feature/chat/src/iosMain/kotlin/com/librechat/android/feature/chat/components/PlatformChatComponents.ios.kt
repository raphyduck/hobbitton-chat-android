package com.librechat.android.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import com.librechat.android.core.common.ChatLayoutConstants
import com.librechat.android.core.model.ContentType
import com.librechat.android.core.model.Message
import com.librechat.android.core.model.MessageContentPart
import com.librechat.android.core.ui.components.AvatarImage
import com.librechat.android.core.ui.components.endpointIconPainter
import com.librechat.android.core.ui.components.isMonochromeEndpointIcon
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import kotlinx.coroutines.delay
import librechat_android.feature.chat.generated.resources.Res
import librechat_android.feature.chat.generated.resources.cd_collapse
import librechat_android.feature.chat.generated.resources.cd_embedded_image
import librechat_android.feature.chat.generated.resources.cd_expand
import librechat_android.feature.chat.generated.resources.cd_expand_table
import librechat_android.feature.chat.generated.resources.cd_close
import librechat_android.feature.chat.generated.resources.cd_failed_to_load_image
import librechat_android.feature.chat.generated.resources.dialog_table
import librechat_android.feature.chat.generated.resources.label_input
import librechat_android.feature.chat.generated.resources.label_output
import librechat_android.feature.chat.generated.resources.label_thinking
import librechat_android.feature.chat.generated.resources.sender_assistant
import librechat_android.feature.chat.generated.resources.sender_you
import org.jetbrains.compose.resources.stringResource
import com.librechat.android.feature.chat.components.artifact.ArtifactButton
import com.librechat.android.feature.chat.components.artifact.ArtifactPanel
import com.librechat.android.feature.chat.components.artifact.ArtifactSegment
import com.librechat.android.feature.chat.components.artifact.detectArtifacts
import com.librechat.android.feature.chat.components.artifact.groupArtifactVersions

private const val ACTION_AUTO_HIDE_MILLIS = 30_000L

// ─── MessageBubble ───────────────────────────────────────────────────

@Composable
actual fun MessageBubble(
    message: Message,
    modifier: Modifier,
    siblingIndex: Int,
    siblingCount: Int,
    onSiblingNavigation: ((Int) -> Unit)?,
    onEdit: (() -> Unit)?,
    onRegenerate: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onFeedback: ((String?) -> Unit)?,
    onContinue: (() -> Unit)?,
    onReadAloud: (() -> Unit)?,
    onFork: (() -> Unit)?,
    baseUrl: String,
    fontSizeMultiplier: Float,
    isReading: Boolean,
    currentFeedback: String?,
    isEditing: Boolean,
    editText: String,
    onEditTextChanged: ((String) -> Unit)?,
    onEditSaveAndSubmit: (() -> Unit)?,
    onEditSaveOnly: (() -> Unit)?,
    onEditCancel: (() -> Unit)?,
    userAvatarUrl: String?,
    userName: String?,
    selectedEndpoint: String?,
    showImageDescriptions: Boolean,
    showActionsInitially: Boolean,
    searchQuery: String?,
    isSearchMatch: Boolean,
    isCurrentSearchMatch: Boolean,
    searchFocusedOccurrence: Int,
    onFocusedOccurrencePositioned: ((LayoutCoordinates) -> Unit)?,
    useKatex: Boolean,
    chatLayoutStyle: String,
    showAvatars: Boolean,
    showBubbles: Boolean,
) {
    val resolvedEndpoint = message.endpoint ?: selectedEndpoint
    val tintEndpointIcon = isMonochromeEndpointIcon(resolvedEndpoint)

    if (chatLayoutStyle == ChatLayoutConstants.TWO_SIDED) {
        TwoSidedBubble(
            message, modifier, siblingIndex, siblingCount, onSiblingNavigation, onEdit, onRegenerate, onCopy, onFeedback,
            onContinue, onReadAloud, onFork, baseUrl, fontSizeMultiplier, useKatex, isReading, currentFeedback,
            isEditing, editText, onEditTextChanged, onEditSaveAndSubmit, onEditSaveOnly, onEditCancel,
            userAvatarUrl, userName, resolvedEndpoint, tintEndpointIcon, showImageDescriptions, showActionsInitially,
            searchQuery, isSearchMatch, isCurrentSearchMatch, searchFocusedOccurrence, onFocusedOccurrencePositioned,
            showAvatars, showBubbles,
        )
    } else {
        ThreadBubble(
            message, modifier, siblingIndex, siblingCount, onSiblingNavigation, onEdit, onRegenerate, onCopy, onFeedback,
            onContinue, onReadAloud, onFork, baseUrl, fontSizeMultiplier, useKatex, isReading, currentFeedback,
            isEditing, editText, onEditTextChanged, onEditSaveAndSubmit, onEditSaveOnly, onEditCancel,
            userAvatarUrl, userName, resolvedEndpoint, tintEndpointIcon, showImageDescriptions, showActionsInitially,
            searchQuery, isSearchMatch, isCurrentSearchMatch, searchFocusedOccurrence, onFocusedOccurrencePositioned,
            showAvatars, showBubbles,
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun ThreadBubble(
    message: Message, modifier: Modifier, siblingIndex: Int, siblingCount: Int,
    onSiblingNavigation: ((Int) -> Unit)?, onEdit: (() -> Unit)?, onRegenerate: (() -> Unit)?,
    onCopy: (() -> Unit)?, onFeedback: ((String?) -> Unit)?, onContinue: (() -> Unit)?,
    onReadAloud: (() -> Unit)?, onFork: (() -> Unit)?, baseUrl: String, fontSizeMultiplier: Float,
    useKatex: Boolean, isReading: Boolean, currentFeedback: String?, isEditing: Boolean, editText: String,
    onEditTextChanged: ((String) -> Unit)?, onEditSaveAndSubmit: (() -> Unit)?, onEditSaveOnly: (() -> Unit)?,
    onEditCancel: (() -> Unit)?, userAvatarUrl: String?, userName: String?,
    resolvedEndpoint: String?, tintEndpointIcon: Boolean,
    showImageDescriptions: Boolean, showActionsInitially: Boolean, searchQuery: String?,
    isSearchMatch: Boolean, isCurrentSearchMatch: Boolean, searchFocusedOccurrence: Int,
    onFocusedOccurrencePositioned: ((LayoutCoordinates) -> Unit)?, showAvatars: Boolean, showBubbles: Boolean,
) {
    val isUser = message.isCreatedByUser
    var showActions by remember(message.messageId) { mutableStateOf(showActionsInitially) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    if (showFeedbackDialog && onFeedback != null) {
        FeedbackCommentDialog(
            onSubmit = { showFeedbackDialog = false; onFeedback("thumbsDown") },
            onDismiss = { showFeedbackDialog = false },
        )
    }

    LaunchedEffect(showActions) { if (showActions) { delay(ACTION_AUTO_HIDE_MILLIS); showActions = false } }

    val searchBg = when {
        isCurrentSearchMatch -> SearchHighlightOrange.copy(alpha = 0.18f)
        isSearchMatch -> SearchHighlightYellow.copy(alpha = 0.12f)
        else -> null
    }

    Column(
        modifier = modifier.fillMaxWidth()
            .then(if (searchBg != null) Modifier.background(searchBg, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(interactionSource = null, indication = null) { showActions = !showActions }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showAvatars) {
                AvatarImage(
                    imageUrl = if (isUser) userAvatarUrl else message.iconURL,
                    fallbackText = if (isUser) userName ?: stringResource(Res.string.sender_you)
                    else message.sender ?: stringResource(Res.string.sender_assistant),
                    fallbackIconPainter = if (!isUser && message.iconURL == null) endpointIconPainter(resolvedEndpoint) else null,
                    showPersonIcon = isUser && userAvatarUrl == null,
                    tintIcon = if (!isUser && message.iconURL == null) tintEndpointIcon else false,
                    size = 28.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (isUser) userName ?: stringResource(Res.string.sender_you)
                else message.sender ?: stringResource(Res.string.sender_assistant),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            message.createdAt?.let { ts -> Spacer(Modifier.width(8.dp)); MessageTimestamp(isoTimestamp = ts) }
        }

        Spacer(Modifier.height(4.dp))

        val contentPad = if (showAvatars) 36.dp else 0.dp
        val bubbleBg = if (showBubbles) {
            if (isUser) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        } else null

        Column(
            modifier = Modifier.padding(start = contentPad).then(
                if (bubbleBg != null) Modifier.background(bubbleBg, BubbleShape).padding(12.dp) else Modifier,
            ),
        ) {
            MessageContentAndActions(
                message, isUser, isEditing, editText, onEditTextChanged, onEditSaveAndSubmit, onEditSaveOnly, onEditCancel,
                baseUrl, fontSizeMultiplier, useKatex, showImageDescriptions, searchQuery, isSearchMatch,
                isCurrentSearchMatch, searchFocusedOccurrence, onFocusedOccurrencePositioned, showActions,
                siblingIndex, siblingCount, onSiblingNavigation, onEdit, onRegenerate, onCopy, onFeedback,
                onContinue, onReadAloud, onFork, isReading, currentFeedback, { showFeedbackDialog = true },
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun TwoSidedBubble(
    message: Message, modifier: Modifier, siblingIndex: Int, siblingCount: Int,
    onSiblingNavigation: ((Int) -> Unit)?, onEdit: (() -> Unit)?, onRegenerate: (() -> Unit)?,
    onCopy: (() -> Unit)?, onFeedback: ((String?) -> Unit)?, onContinue: (() -> Unit)?,
    onReadAloud: (() -> Unit)?, onFork: (() -> Unit)?, baseUrl: String, fontSizeMultiplier: Float,
    useKatex: Boolean, isReading: Boolean, currentFeedback: String?, isEditing: Boolean, editText: String,
    onEditTextChanged: ((String) -> Unit)?, onEditSaveAndSubmit: (() -> Unit)?, onEditSaveOnly: (() -> Unit)?,
    onEditCancel: (() -> Unit)?, userAvatarUrl: String?, userName: String?,
    resolvedEndpoint: String?, tintEndpointIcon: Boolean,
    showImageDescriptions: Boolean, showActionsInitially: Boolean, searchQuery: String?,
    isSearchMatch: Boolean, isCurrentSearchMatch: Boolean, searchFocusedOccurrence: Int,
    onFocusedOccurrencePositioned: ((LayoutCoordinates) -> Unit)?, showAvatars: Boolean, showBubbles: Boolean,
) {
    val isUser = message.isCreatedByUser
    var showActions by remember(message.messageId) { mutableStateOf(showActionsInitially) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    if (showFeedbackDialog && onFeedback != null) {
        FeedbackCommentDialog(
            onSubmit = { showFeedbackDialog = false; onFeedback("thumbsDown") },
            onDismiss = { showFeedbackDialog = false },
        )
    }

    LaunchedEffect(showActions) { if (showActions) { delay(ACTION_AUTO_HIDE_MILLIS); showActions = false } }

    val searchBg = when {
        isCurrentSearchMatch -> SearchHighlightOrange.copy(alpha = 0.18f)
        isSearchMatch -> SearchHighlightYellow.copy(alpha = 0.12f)
        else -> null
    }
    val bubbleBg = if (showBubbles) {
        if (isUser) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    } else null
    val effectiveBg = if (searchBg != null && bubbleBg != null) searchBg.compositeOver(bubbleBg)
    else searchBg ?: bubbleBg

    Row(
        modifier = modifier.fillMaxWidth()
            .clickable(interactionSource = null, indication = null) { showActions = !showActions }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser && showAvatars) {
            AvatarImage(
                imageUrl = message.iconURL,
                fallbackText = message.sender ?: stringResource(Res.string.sender_assistant),
                fallbackIconPainter = if (message.iconURL == null) endpointIconPainter(resolvedEndpoint) else null,
                tintIcon = if (message.iconURL == null) tintEndpointIcon else false,
                size = 28.dp,
            )
            Spacer(Modifier.width(6.dp))
        }

        Column(
            modifier = Modifier.weight(1f).then(
                if (effectiveBg != null) Modifier.background(effectiveBg, BubbleShape).padding(12.dp)
                else Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            ),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isUser) userName ?: stringResource(Res.string.sender_you)
                    else message.sender ?: stringResource(Res.string.sender_assistant),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (showBubbles) {
                        if (isUser) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    } else MaterialTheme.colorScheme.onSurface,
                )
                message.createdAt?.let { ts -> Spacer(Modifier.width(6.dp)); MessageTimestamp(isoTimestamp = ts) }
            }
            Spacer(Modifier.height(4.dp))

            MessageContentAndActions(
                message, isUser, isEditing, editText, onEditTextChanged, onEditSaveAndSubmit, onEditSaveOnly, onEditCancel,
                baseUrl, fontSizeMultiplier, useKatex, showImageDescriptions, searchQuery, isSearchMatch,
                isCurrentSearchMatch, searchFocusedOccurrence, onFocusedOccurrencePositioned, showActions,
                siblingIndex, siblingCount, onSiblingNavigation, onEdit, onRegenerate, onCopy, onFeedback,
                onContinue, onReadAloud, onFork, isReading, currentFeedback, { showFeedbackDialog = true },
            )
        }

        if (isUser && showAvatars) {
            Spacer(Modifier.width(6.dp))
            AvatarImage(
                imageUrl = userAvatarUrl,
                fallbackText = userName ?: stringResource(Res.string.sender_you),
                showPersonIcon = userAvatarUrl == null,
                size = 28.dp,
            )
        }
    }
}


// ─── ContentPartRenderer ─────────────────────────────────────────────

@Composable
actual fun ContentPartRenderer(
    part: MessageContentPart, modifier: Modifier, baseUrl: String, fontSizeMultiplier: Float,
    useKatex: Boolean, attachments: List<com.librechat.android.core.model.Attachment>,
    showImageDescriptions: Boolean, searchQuery: String?, searchFocusedOccurrence: Int,
    onFocusedOccurrencePositioned: ((LayoutCoordinates) -> Unit)?,
) {
    val mod = modifier.fillMaxWidth()
    when (part.type) {
        ContentType.TEXT, ContentType.TEXT_DELTA -> {
            val text = part.text.orEmpty()
            if (text.isNotBlank()) {
                val segments = remember(text) { detectArtifacts(text) }
                val hasArtifacts = remember(segments) { segments.any { it is ArtifactSegment.ArtifactReference } }
                if (!hasArtifacts) {
                    MarkdownContent(text, mod, fontSizeMultiplier, useKatex, searchQuery, searchFocusedOccurrence, onFocusedOccurrencePositioned)
                } else {
                    val versionMap = remember(segments) { groupArtifactVersions(segments) }
                    var activeArtifact by remember {
                        mutableStateOf<com.librechat.android.feature.chat.components.artifact.Artifact?>(null)
                    }
                    Column(modifier = mod) {
                        segments.forEach { segment ->
                            when (segment) {
                                is ArtifactSegment.Text -> {
                                    MarkdownContent(segment.text, Modifier.fillMaxWidth(), fontSizeMultiplier, useKatex, searchQuery, searchFocusedOccurrence, onFocusedOccurrencePositioned)
                                }
                                is ArtifactSegment.ArtifactReference -> {
                                    val versions = versionMap[segment.artifact.identifier] ?: listOf(segment.artifact)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ArtifactButton(
                                        artifact = segment.artifact,
                                        onClick = { activeArtifact = segment.artifact },
                                        versionCount = versions.size,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                    activeArtifact?.let { artifact ->
                        val versions = versionMap[artifact.identifier] ?: listOf(artifact)
                        ArtifactPanel(
                            artifact = artifact,
                            onDismiss = { activeArtifact = null },
                            versions = versions,
                        )
                    }
                }
            }
        }
        ContentType.THINK -> ThinkingBlock(part.think.orEmpty(), mod, fontSizeMultiplier, useKatex)
        ContentType.TOOL_CALL -> {
            val tc = part.toolCall
            ToolCallCard(tc?.name ?: tc?.function?.name ?: "Tool Call", tc?.function?.arguments, tc?.output ?: tc?.function?.output, mod)
        }
        ContentType.IMAGE_FILE -> {
            val url = part.imageFile?.filepath?.let { fp ->
                when {
                    fp.startsWith("http") -> fp
                    fp.startsWith("/images/") && baseUrl.isNotBlank() -> "$baseUrl$fp"
                    baseUrl.isNotBlank() -> "$baseUrl/api/files/$fp"
                    else -> fp
                }
            } ?: part.imageFile?.fileId?.let { if (baseUrl.isNotBlank()) "$baseUrl/api/files/$it" else null }
            ImageContentPart(url, mod)
        }
        ContentType.IMAGE_URL -> ImageContentPart(part.imageUrl?.url, mod)
        ContentType.VIDEO_URL -> part.videoUrl?.url?.let { VideoContent(it, mod) }
        ContentType.INPUT_AUDIO -> AudioContent(part.inputAudio?.data, part.inputAudio?.format, mod)
        ContentType.ERROR -> ErrorContentPart(part.error ?: part.text.orEmpty(), mod)
        else -> if (!part.text.isNullOrEmpty()) MarkdownContent(part.text.orEmpty(), mod, fontSizeMultiplier, useKatex)
    }
}

// ─── MarkdownContent ─────────────────────────────────────────────────

private val TABLE_SEPARATOR_REGEX = Regex("^\\|?\\s*:?-{1,}:?\\s*(\\|\\s*:?-{1,}:?\\s*)*\\|?$")

private enum class TableCellAlignment { LEFT, CENTER, RIGHT }

@Composable
actual fun MarkdownContent(
    text: String, modifier: Modifier, fontSizeMultiplier: Float, useKatex: Boolean,
    searchQuery: String?, searchFocusedOccurrence: Int,
    onFocusedOccurrencePositioned: ((LayoutCoordinates) -> Unit)?,
) {
    val segments = remember(text) { splitTableSegments(text) }

    val colors = markdownColor(
        text = MaterialTheme.colorScheme.onSurface, codeText = MaterialTheme.colorScheme.onSurface,
        linkText = MaterialTheme.colorScheme.primary, codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
        inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHigh, dividerColor = MaterialTheme.colorScheme.outlineVariant,
    )
    val bl = MaterialTheme.typography.bodyLarge
    val bm = MaterialTheme.typography.bodyMedium
    val typography = markdownTypography(
        h1 = MaterialTheme.typography.headlineLarge.scale(fontSizeMultiplier),
        h2 = MaterialTheme.typography.headlineMedium.scale(fontSizeMultiplier),
        h3 = MaterialTheme.typography.headlineSmall.scale(fontSizeMultiplier),
        h4 = MaterialTheme.typography.titleLarge.scale(fontSizeMultiplier),
        h5 = MaterialTheme.typography.titleMedium.scale(fontSizeMultiplier),
        h6 = MaterialTheme.typography.titleSmall.scale(fontSizeMultiplier),
        text = bl.scale(fontSizeMultiplier), paragraph = bl.scale(fontSizeMultiplier),
        quote = bl.copy(fontStyle = FontStyle.Italic).scale(fontSizeMultiplier),
        code = bm.copy(fontFamily = FontFamily.Monospace).scale(fontSizeMultiplier),
        inlineCode = bl.copy(fontFamily = FontFamily.Monospace).scale(fontSizeMultiplier),
        ordered = bl.scale(fontSizeMultiplier), bullet = bl.scale(fontSizeMultiplier), list = bl.scale(fontSizeMultiplier),
    )

    Column(modifier = modifier.fillMaxWidth()) {
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is MdSegment.Text -> key(fontSizeMultiplier, index) {
                    Markdown(content = segment.content, colors = colors, typography = typography, flavour = GFMFlavourDescriptor(), modifier = Modifier.fillMaxWidth())
                }
                is MdSegment.Table -> {
                    if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                    IosMarkdownTableWithFullscreen(
                        headers = segment.headers,
                        alignments = segment.alignments,
                        rows = segment.rows,
                        fontSizeMultiplier = fontSizeMultiplier,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    if (index < segments.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

// ─── Table segment model ────────────────────────────────────────────

private sealed interface MdSegment {
    data class Text(val content: String) : MdSegment
    data class Table(
        val headers: List<String>,
        val alignments: List<TableCellAlignment>,
        val rows: List<List<String>>,
    ) : MdSegment
}

private fun splitTableSegments(text: String): List<MdSegment> {
    val lines = text.split('\n')
    val result = mutableListOf<MdSegment>()
    val buffer = mutableListOf<String>()
    var i = 0

    while (i < lines.size) {
        if (i + 2 < lines.size && isTableRow(lines[i]) && isTableSeparator(lines[i + 1])) {
            if (buffer.isNotEmpty()) {
                val preceding = buffer.joinToString("\n").trim()
                if (preceding.isNotEmpty()) result.add(MdSegment.Text(preceding))
                buffer.clear()
            }
            val headerCells = parseTableRow(lines[i])
            val alignments = parseAlignments(lines[i + 1], headerCells.size)
            val dataRows = mutableListOf<List<String>>()
            var j = i + 2
            while (j < lines.size && isTableRow(lines[j])) {
                val rowCells = parseTableRow(lines[j])
                dataRows.add(List(headerCells.size) { col -> rowCells.getOrElse(col) { "" } })
                j++
            }
            if (dataRows.isNotEmpty()) {
                result.add(MdSegment.Table(headerCells, alignments, dataRows))
                i = j
            } else {
                buffer.add(lines[i]); buffer.add(lines[i + 1]); i += 2
            }
        } else {
            buffer.add(lines[i]); i++
        }
    }
    if (buffer.isNotEmpty()) {
        val remaining = buffer.joinToString("\n").trim()
        if (remaining.isNotEmpty()) result.add(MdSegment.Text(remaining))
    }
    return result
}

private fun isTableRow(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    return trimmed.startsWith('|') || trimmed.endsWith('|') || trimmed.count { it == '|' } >= 2
}

private fun isTableSeparator(line: String): Boolean = TABLE_SEPARATOR_REGEX.matches(line.trim())

private fun parseTableRow(line: String): List<String> {
    val inner = line.trim().removePrefix("|").removeSuffix("|")
    return inner.split('|').map { it.trim() }
}

private fun parseAlignments(separatorLine: String, columnCount: Int): List<TableCellAlignment> {
    val cells = parseTableRow(separatorLine)
    return List(columnCount) { col ->
        val cell = cells.getOrElse(col) { "---" }.trim()
        when {
            cell.startsWith(':') && cell.endsWith(':') -> TableCellAlignment.CENTER
            cell.endsWith(':') -> TableCellAlignment.RIGHT
            else -> TableCellAlignment.LEFT
        }
    }
}

// ─── Table with fullscreen expand ───────────────────────────────────

@Composable
private fun IosMarkdownTableWithFullscreen(
    headers: List<String>,
    alignments: List<TableCellAlignment>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    fontSizeMultiplier: Float = 1.0f,
) {
    var showFullscreen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IosMarkdownTable(
            headers = headers,
            alignments = alignments,
            rows = rows,
            fontSizeMultiplier = fontSizeMultiplier,
        )

        IconButton(
            onClick = { showFullscreen = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = stringResource(Res.string.cd_expand_table),
                modifier = Modifier.size(18.dp),
            )
        }
    }

    if (showFullscreen) {
        IosFullscreenTableDialog(
            headers = headers,
            alignments = alignments,
            rows = rows,
            fontSizeMultiplier = fontSizeMultiplier,
            onDismiss = { showFullscreen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosFullscreenTableDialog(
    headers: List<String>,
    alignments: List<TableCellAlignment>,
    rows: List<List<String>>,
    fontSizeMultiplier: Float,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            TopAppBar(
                title = { Text(stringResource(Res.string.dialog_table)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.cd_close),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                IosMarkdownTable(
                    headers = headers,
                    alignments = alignments,
                    rows = rows,
                    fontSizeMultiplier = fontSizeMultiplier,
                )
            }
        }
    }
}

// ─── Table composable ───────────────────────────────────────────────

@Composable
private fun IosMarkdownTable(
    headers: List<String>,
    alignments: List<TableCellAlignment>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    fontSizeMultiplier: Float = 1.0f,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val headerBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val bodyStyle = MaterialTheme.typography.bodyMedium.scale(fontSizeMultiplier)
    val headerStyle = bodyStyle.copy(fontWeight = FontWeight.Bold)
    val cellPaddingH = 12.dp
    val cellPaddingV = 8.dp

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val columnCount = headers.size

    val columnWidths = remember(headers, rows, fontSizeMultiplier) {
        val cellPaddingPx = with(density) { (cellPaddingH * 2).roundToPx() }
        List(columnCount) { col ->
            var maxWidth = textMeasurer.measure(text = headers[col], style = headerStyle).size.width
            for (row in rows) {
                val measured = textMeasurer.measure(text = row.getOrElse(col) { "" }, style = bodyStyle).size.width
                if (measured > maxWidth) maxWidth = measured
            }
            with(density) { (maxWidth + cellPaddingPx).toDp() }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).clip(MaterialTheme.shapes.small),
    ) {
        Column {
            Row(modifier = Modifier.height(IntrinsicSize.Min).background(headerBackground)) {
                headers.forEachIndexed { colIndex, header ->
                    if (colIndex > 0) VerticalDivider(color = dividerColor, modifier = Modifier.fillMaxHeight())
                    TableCell(header, headerStyle, textColor, alignments.getOrElse(colIndex) { TableCellAlignment.LEFT }, columnWidths[colIndex], cellPaddingH, cellPaddingV)
                }
            }
            HorizontalDivider(color = dividerColor, thickness = 1.dp)
            rows.forEachIndexed { rowIndex, row ->
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    row.forEachIndexed { colIndex, cell ->
                        if (colIndex > 0) VerticalDivider(color = dividerColor, modifier = Modifier.fillMaxHeight())
                        TableCell(cell, bodyStyle, textColor, alignments.getOrElse(colIndex) { TableCellAlignment.LEFT }, columnWidths.getOrElse(colIndex) { columnWidths.last() }, cellPaddingH, cellPaddingV)
                    }
                }
                if (rowIndex < rows.lastIndex) HorizontalDivider(color = dividerColor, thickness = Dp.Hairline)
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color,
    alignment: TableCellAlignment, cellWidth: Dp, paddingH: Dp, paddingV: Dp, modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.width(cellWidth).padding(horizontal = paddingH, vertical = paddingV),
        contentAlignment = when (alignment) {
            TableCellAlignment.LEFT -> Alignment.CenterStart
            TableCellAlignment.CENTER -> Alignment.Center
            TableCellAlignment.RIGHT -> Alignment.CenterEnd
        },
    ) {
        Text(
            text = text, style = style, color = color, maxLines = 10, overflow = TextOverflow.Ellipsis,
            textAlign = when (alignment) {
                TableCellAlignment.LEFT -> TextAlign.Start
                TableCellAlignment.CENTER -> TextAlign.Center
                TableCellAlignment.RIGHT -> TextAlign.End
            },
        )
    }
}

private fun androidx.compose.ui.text.TextStyle.scale(m: Float): androidx.compose.ui.text.TextStyle {
    if (m == 1.0f) return this
    return copy(
        fontSize = if (fontSize.isSpecified) (fontSize.value * m).sp else fontSize,
        lineHeight = if (lineHeight.isSpecified) (lineHeight.value * m).sp else lineHeight,
    )
}

// ─── Sub-components ──────────────────────────────────────────────────

@Composable
private fun ThinkingBlock(text: String, modifier: Modifier = Modifier, fontSizeMultiplier: Float = 1f, useKatex: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Psychology, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.label_thinking), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, stringResource(if (expanded) Res.string.cd_collapse else Res.string.cd_expand), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Spacer(Modifier.height(8.dp))
                MarkdownContent(text, fontSizeMultiplier = fontSizeMultiplier, useKatex = useKatex)
            }
        }
    }
}

@Composable
private fun ToolCallCard(toolName: String, args: String?, output: String?, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(toolName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, stringResource(if (expanded) Res.string.cd_collapse else Res.string.cd_expand), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    if (!args.isNullOrBlank()) { Spacer(Modifier.height(8.dp)); Text(stringResource(Res.string.label_input), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(4.dp)); CodeBlock(code = args, language = "json") }
                    if (!output.isNullOrBlank()) { Spacer(Modifier.height(8.dp)); Text(stringResource(Res.string.label_output), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(4.dp)); CodeBlock(code = output, language = null) }
                }
            }
        }
    }
}

@Composable
private fun ImageContentPart(imageUrl: String?, modifier: Modifier = Modifier) {
    if (imageUrl == null) return
    var showFs by remember { mutableStateOf(false) }
    SubcomposeAsyncImage(
        model = imageUrl, contentDescription = stringResource(Res.string.cd_embedded_image), contentScale = ContentScale.FillWidth,
        modifier = modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(12.dp)).clickable { showFs = true },
        loading = { Box(Modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } },
        error = { Box(Modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)), Alignment.Center) { Icon(Icons.Default.BrokenImage, stringResource(Res.string.cd_failed_to_load_image), Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
    if (showFs) FullscreenImageViewer(imageUrl, { showFs = false })
}

@Composable
private fun ErrorContentPart(errorText: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(12.dp), verticalAlignment = Alignment.Top) {
        Text(errorText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}
