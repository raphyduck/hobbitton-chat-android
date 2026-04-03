package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import librechat_android.feature.chat.generated.resources.Res
import librechat_android.feature.chat.generated.resources.*

/**
 * Data class representing a single web search result parsed from tool call output.
 */
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val favicon: String? = null,
)

/**
 * Card displaying a web search result with favicon, title, URL, and expandable snippet.
 * Clicking the card opens the URL in an external browser.
 */
@Composable
fun WebSearchResultCard(
    result: WebSearchResult,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var isSnippetExpanded by remember { mutableStateOf(false) }

    val searchResultCd = stringResource(Res.string.cd_search_result, result.title)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                try {
                    uriHandler.openUri(result.url)
                } catch (_: Exception) {
                    // URL might be malformed
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = searchResultCd
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Favicon
            val faviconUrl = result.favicon
                ?: result.url.let { url ->
                    try {
                        // Extract host from URL without android.net.Uri (KMP-compatible)
                        val withoutScheme = url.substringAfter("://")
                        val host = withoutScheme.substringBefore("/").substringBefore(":")
                        if (host.isNotEmpty()) "https://www.google.com/s2/favicons?domain=$host&sz=48" else null
                    } catch (_: Exception) {
                        null
                    }
                }

            if (faviconUrl != null) {
                SubcomposeAsyncImage(
                    model = faviconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape),
                    error = {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(2.dp))

                // URL
                Text(
                    text = result.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (result.snippet.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Snippet (expandable)
                    Text(
                        text = result.snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isSnippetExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(
                            onClick = { isSnippetExpanded = !isSnippetExpanded },
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Renders a list of web search results as stacked cards.
 */
@Composable
fun WebSearchResultList(
    results: List<WebSearchResult>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        results.forEachIndexed { index, result ->
            WebSearchResultCard(result = result)
            if (index < results.lastIndex) {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}
