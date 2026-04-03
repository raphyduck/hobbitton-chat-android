package com.librechat.android.core.common.extensions

fun String.trimTrailingSlash(): String = trimEnd('/')

fun String.isValidUrl(): Boolean =
    startsWith("http://") || startsWith("https://")

fun String.ensureHttps(): String = when {
    startsWith("https://") -> this
    startsWith("http://") -> replaceFirst("http://", "https://")
    else -> "https://$this"
}

fun String.truncate(maxLength: Int, ellipsis: String = "..."): String =
    if (length <= maxLength) {
        this
    } else {
        take(maxLength - ellipsis.length) + ellipsis
    }
