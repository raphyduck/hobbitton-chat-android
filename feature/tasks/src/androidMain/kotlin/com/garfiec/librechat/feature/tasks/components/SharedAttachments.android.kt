package com.garfiec.librechat.feature.tasks.components

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import com.garfiec.librechat.feature.tasks.util.StagedAttachment

/**
 * The files another app shares into Eva (09/11/2026), staged exactly as the composer's picker
 * stages what the person picks: a photo shrunk to what a vision model reads, a video or a large
 * file kept for the deposit with its first frame, anything else read whole within the message's
 * ceiling. A URI that cannot be read stages nothing, as in the picker. Blocking reads: call it off
 * the main thread.
 *
 * Only another app's `content://` URIs are staged (S1, 19/11/2026, [isForeignContentUri]): the
 * activity is exported, and a share naming `file://` or one of this app's own providers would have
 * it read its private files into the composer.
 */
fun stageSharedUris(context: Context, uris: List<String>): List<StagedAttachment> {
    val foreign = uris.filter { isForeignContentUri(it, context.packageName) }
    if (foreign.size < uris.size) {
        Logger.w { "Share: ${uris.size - foreign.size} item(s) refused: not another app's content" }
    }
    return foreign.mapNotNull { raw ->
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return@mapNotNull null
        context.stage(uri)
    }
}

/**
 * True for a content URI another app grants (S1, 19/11/2026). The activity is exported: a share
 * naming file:// or one of this app's own providers would have it read its private files (secrets,
 * journal, transcripts) into the composer.
 *
 * The authority is read off the string, not through `android.net.Uri`: a JVM test with
 * `isReturnDefaultValues` would get nulls from it and pass for the wrong reason. The userinfo is
 * compared after its `@`, so `content://0@<own>.files/…` reaches the same provider and is refused.
 * Refused too: every provider of this app, its `.files` FileProvider and the `androidx-startup` one
 * the manifest merge adds (and LeakCanary's in debug), since the app reads its own ones unexported.
 */
internal fun isForeignContentUri(raw: String, ownPackage: String): Boolean {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("content://", ignoreCase = true)) return false
    val authority = trimmed.substring("content://".length)
        .takeWhile { it != '/' && it != '?' && it != '#' }
        .substringAfterLast('@')
        .lowercase()
    if (authority.isEmpty()) return false
    val own = ownPackage.lowercase()
    return authority != own && !authority.startsWith("$own.")
}
