package com.garfiec.librechat.feature.files.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.ui.components.FilterChipBar
import com.garfiec.librechat.feature.files.viewmodel.FileTypeFilter

/** Horizontal FilterChip row that filters files by MIME type prefix (All, Images, Documents, Audio, Video). */
@Composable
fun FileTypeFilterBar(
    selectedFilter: FileTypeFilter,
    onFilterChange: (FileTypeFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChipBar(
        items = FileTypeFilter.entries,
        isSelected = { it == selectedFilter },
        onSelect = onFilterChange,
        label = { it.label },
        modifier = modifier,
    )
}
