package com.garfiec.librechat.feature.conversations.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.ui.components.FilterChipBottomSheet
import librechat_android.feature.conversations.generated.resources.Res
import librechat_android.feature.conversations.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun TagPicker(
    availableTags: List<ConversationTag>,
    currentTags: List<String>,
    onTagsChanged: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allTagStrings = remember(availableTags, currentTags) {
        buildList {
            availableTags.mapNotNull { it.tag }.forEach(::add)
            currentTags.forEach { tag -> if (tag !in this) add(tag) }
        }
    }

    FilterChipBottomSheet(
        items = allTagStrings,
        selectedItems = currentTags.toSet(),
        onSelectionChanged = { onTagsChanged(it.toList()) },
        label = { it },
        onDismiss = onDismiss,
        title = stringResource(Res.string.tags),
        emptyMessage = stringResource(Res.string.no_tags_yet),
        onAdd = { newTag -> newTag },
        addPlaceholder = stringResource(Res.string.add_new_tag),
        addContentDescription = stringResource(Res.string.cd_add_tag),
        modifier = modifier,
    )
}
