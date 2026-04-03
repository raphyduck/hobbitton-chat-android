package com.garfiec.librechat.shared.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.endpointIconPainter
import com.garfiec.librechat.core.ui.components.isMonochromeIcon
import librechat_android.shared.generated.resources.Res
import librechat_android.shared.generated.resources.agents
import librechat_android.shared.generated.resources.bookmark
import librechat_android.shared.generated.resources.cd_clear_search
import librechat_android.shared.generated.resources.cd_search
import librechat_android.shared.generated.resources.favorites
import librechat_android.shared.generated.resources.files
import librechat_android.shared.generated.resources.new_chat
import librechat_android.shared.generated.resources.no_conversations_found
import librechat_android.shared.generated.resources.remove_bookmark
import librechat_android.shared.generated.resources.search_conversations_placeholder
import librechat_android.shared.generated.resources.settings
import org.jetbrains.compose.resources.stringResource

// Pre-computed shapes to avoid creating new ones per item per frame
private val ItemShape = RoundedCornerShape(8.dp)
private val ActiveIndicatorShape = RoundedCornerShape(2.dp)

/**
 * Stateful DrawerContent that collects its own state from the ViewModel.
 */
@Composable
fun DrawerContent(
    viewModel: NavHostViewModel,
    onNewChat: () -> Unit,
    onConversationClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onAgentsClick: () -> Unit,
    onFilesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.drawerUiState.collectAsStateWithLifecycle()
    DrawerContent(
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onNewChat = onNewChat,
        onConversationClick = onConversationClick,
        onSettingsClick = onSettingsClick,
        onAgentsClick = onAgentsClick,
        onFilesClick = onFilesClick,
        onToggleFavorite = viewModel::toggleFavorite,
        onRefresh = viewModel::refreshConversations,
        onLoadMore = viewModel::loadMoreConversations,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerContent(
    uiState: DrawerUiState,
    onSearchQueryChanged: (String) -> Unit,
    onNewChat: () -> Unit,
    onConversationClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onAgentsClick: () -> Unit,
    onFilesClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .statusBarsPadding()
            .padding(top = 16.dp),
    ) {
        // "New Chat" button at top
        Surface(
            onClick = onNewChat,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = ItemShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.new_chat),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChanged,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(Res.string.cd_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.cd_clear_search),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
            placeholder = {
                Text(
                    text = stringResource(Res.string.search_conversations_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            singleLine = true,
            shape = ItemShape,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Conversation list with favorites section and date groups
        val listState = rememberLazyListState()

        val shouldLoadMore = remember {
            derivedStateOf {
                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = listState.layoutInfo.totalItemsCount
                lastVisibleItem >= totalItems - 8 && totalItems > 0
            }
        }

        LaunchedEffect(shouldLoadMore.value) {
            if (shouldLoadMore.value && uiState.hasMore && !uiState.isLoadingMore) {
                onLoadMore()
            }
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Favorites section
                if (uiState.favoriteConversations.isNotEmpty() && uiState.searchQuery.isEmpty()) {
                    item(key = "favorites_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 4.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(Res.string.favorites),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                    }

                    items(
                        items = uiState.favoriteConversations,
                        key = { "fav_${it.conversationId}" },
                        contentType = { "conversation" },
                    ) { data ->
                        DrawerConversationItem(
                            data = data,
                            onClick = { onConversationClick(data.conversationId) },
                            onToggleFavorite = { onToggleFavorite(data.conversationId) },
                        )
                    }

                    item(key = "favorites_divider") {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }

                if (uiState.groupedConversations.isEmpty() && uiState.searchQuery.isNotEmpty()) {
                    item(key = "empty_search") {
                        Text(
                            text = stringResource(Res.string.no_conversations_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        )
                    }
                }

                uiState.groupedConversations.forEach { (dateGroup, displayItems) ->
                    item(key = "header_$dateGroup") {
                        Text(
                            text = dateGroup,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 12.dp,
                                bottom = 4.dp,
                            ),
                        )
                    }

                    items(
                        items = displayItems,
                        key = { it.conversationId },
                        contentType = { "conversation" },
                    ) { data ->
                        DrawerConversationItem(
                            data = data,
                            onClick = { onConversationClick(data.conversationId) },
                            onToggleFavorite = { onToggleFavorite(data.conversationId) },
                        )
                    }
                }

                if (uiState.isLoadingMore) {
                    item(key = "loading_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }

        // Bottom section: divider + footer links
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        DrawerFooterItem(
            icon = Icons.Default.SmartToy,
            label = stringResource(Res.string.agents),
            onClick = onAgentsClick,
        )
        DrawerFooterItem(
            icon = Icons.Default.Folder,
            label = stringResource(Res.string.files),
            onClick = onFilesClick,
        )
        DrawerFooterItem(
            icon = Icons.Default.Settings,
            label = stringResource(Res.string.settings),
            onClick = onSettingsClick,
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun DrawerConversationItem(
    data: DrawerConversationDisplayData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: () -> Unit = {},
) {
    val iconPainter = data.endpoint?.let { endpointIconPainter(it) }

    val backgroundColor = if (data.isActive) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .fillMaxWidth()
            .background(backgroundColor, ItemShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (data.isActive) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.primary, ActiveIndicatorShape),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        if (data.isFavorite) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else if (iconPainter != null) {
            val isMonochrome = data.endpoint?.isMonochromeIcon() == true
            Icon(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isMonochrome) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    Color.Unspecified
                },
            )
        } else {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (data.isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = data.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (data.isActive) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val subtitle = remember(data.model, data.relativeTime) {
                buildString {
                    data.model?.let { model ->
                        append(model.take(20))
                    }
                    if (data.relativeTime.isNotEmpty()) {
                        if (isNotEmpty()) append(" \u00B7 ")
                        append(data.relativeTime)
                    }
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Icon(
            imageVector = if (data.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = if (data.isFavorite) {
                stringResource(Res.string.remove_bookmark)
            } else {
                stringResource(Res.string.bookmark)
            },
            modifier = Modifier
                .size(32.dp)
                .clickable(onClick = onToggleFavorite)
                .padding(8.dp),
            tint = if (data.isFavorite) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun DrawerFooterItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
