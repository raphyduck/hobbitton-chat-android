package com.garfiec.librechat.feature.chat

import android.net.Uri

/** More than this many files in one share is not a chat message; the rest is dropped. */
const val MAX_SHARED_FILES = 20

/**
 * Whether a stream URI another app shared into the composer may be read at all.
 *
 * `ContentResolver.openInputStream` happily opens a `file:` URI, with this app's own rights — so
 * a share pointing into the private data directory (the Room database, the preference files)
 * would be read and uploaded to the user's server on the sender's initiative. Only `content:`
 * URIs are accepted, and not from this app's own FileProvider either: nothing legitimate shares
 * our cache back at us, and its paths are ours (review C6, 26/09/2026).
 */
fun isAcceptableSharedUri(scheme: String?, authority: String?, ownFileProviderAuthority: String): Boolean =
    scheme.equals("content", ignoreCase = true) &&
        !authority.isNullOrBlank() &&
        !authority.equals(ownFileProviderAuthority, ignoreCase = true)

/** The URIs of a share this app will stage, in order, bounded by [MAX_SHARED_FILES]. */
fun acceptableSharedUris(uris: List<Uri>, ownFileProviderAuthority: String): List<Uri> =
    uris.filter { isAcceptableSharedUri(it.scheme, it.authority, ownFileProviderAuthority) }
        .take(MAX_SHARED_FILES)
