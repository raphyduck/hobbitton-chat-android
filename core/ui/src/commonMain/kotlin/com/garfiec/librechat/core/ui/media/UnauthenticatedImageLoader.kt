package com.garfiec.librechat.core.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import io.ktor.client.HttpClient
import kotlin.concurrent.Volatile

/**
 * Process-wide, built on first use from the composition (main thread), so the `@Volatile` is a
 * belt for the improbable off-main first reader rather than a lock.
 */
@Volatile
private var shared: ImageLoader? = null

/**
 * An [ImageLoader] whose network fetcher carries **no** session: no bearer, no gateway headers,
 * no Chrome user agent.
 *
 * The singleton loader is built on the authenticated Ktor client, which attaches the bearer to
 * any request for the server's host. An `image_url` content part is written by the model (or a
 * tool it called) and displayed as-is, so a part pointing at the server's host over `http://` or
 * on another port would carry the token in clear or to a neighbouring service. Images that are
 * not under the server's own origin therefore load through this loader instead (review C7,
 * 26/09/2026). It shares the singleton's memory and disk caches so the account purge that clears
 * those clears these images too.
 */
@Composable
fun rememberUnauthenticatedImageLoader(): ImageLoader {
    val context = LocalPlatformContext.current
    return remember(context) { unauthenticatedImageLoader(context) }
}

/** Non-composable access for callers outside a composition. */
fun unauthenticatedImageLoader(context: PlatformContext): ImageLoader =
    shared ?: build(context).also { shared = it }

private fun build(context: PlatformContext): ImageLoader {
    val authenticated = SingletonImageLoader.get(context)
    return ImageLoader.Builder(context)
        .components {
            add(KtorNetworkFetcherFactory(HttpClient()))
            add(SvgDecoder.Factory())
        }
        .memoryCache(authenticated.memoryCache)
        .diskCache(authenticated.diskCache)
        .crossfade(true)
        .build()
}
