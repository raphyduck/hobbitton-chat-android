package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.rememberMarkdownState

/**
 * Renders markdown via the m3 [Markdown] composable, preferring a cached
 * [State.Success] from [LocalParsedMarkdownCache] when available.
 *
 * On cache hit the `Markdown(state = ...)` overload is used directly, skipping
 * the library's `StateFlow<State>` + `LaunchedEffect` parse path — no
 * Loading→Success transient, so a LazyColumn re-entry doesn't briefly render
 * at 0 px and push surrounding content down.
 *
 * On cache miss the standard `rememberMarkdownState(...)` path runs (async
 * parse, retainState=true). A side-effect collector watches the state flow
 * and writes the resulting [State.Success] to the cache for the next encounter.
 *
 * Padding/dimens/etc are left at library defaults — callers that need to
 * customize those should fall back to invoking [Markdown] directly.
 */
@Composable
fun CachedMarkdown(
    content: String,
    colors: MarkdownColors,
    typography: MarkdownTypography,
    modifier: Modifier = Modifier,
    immediate: Boolean = false,
) {
    val cache = LocalParsedMarkdownCache.current
    val key = parsedMarkdownCacheKey(content)
    val cached = cache[key]

    if (cached != null) {
        Markdown(
            state = cached,
            colors = colors,
            typography = typography,
            modifier = modifier,
        )
        return
    }

    val mdState = rememberMarkdownState(
        content = content,
        immediate = immediate,
        retainState = true,
    )
    LaunchedEffect(mdState, key) {
        mdState.state.collect { s ->
            if (s is State.Success) {
                cache.put(key, s)
            }
        }
    }
    Markdown(
        markdownState = mdState,
        colors = colors,
        typography = typography,
        modifier = modifier,
    )
}
