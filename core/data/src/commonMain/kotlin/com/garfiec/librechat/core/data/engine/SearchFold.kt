package com.garfiec.librechat.core.data.engine

/**
 * A title as the session search compares it (21/10/2026): lower case, French and Latin accents
 * folded, ligatures spelt out. « energie » finds « Énergie du Pic », which a plain
 * `contains(ignoreCase = true)` did not. Common code has no `Normalizer`, hence the table.
 */
fun String.foldForSearch(): String = buildString(length) {
    for (character in this@foldForSearch.lowercase()) {
        append(FOLDED[character] ?: character.toString())
    }
}

/** True when [query], folded and trimmed, is empty or appears in this title, folded. */
fun String.matchesSearch(query: String): Boolean {
    val needle = query.trim().foldForSearch()
    return needle.isEmpty() || foldForSearch().contains(needle)
}

private val FOLDED: Map<Char, String> = buildMap {
    "àáâãäå".forEach { put(it, "a") }
    "çć".forEach { put(it, "c") }
    "èéêë".forEach { put(it, "e") }
    "ìíîï".forEach { put(it, "i") }
    "ñ".forEach { put(it, "n") }
    "òóôõö".forEach { put(it, "o") }
    "ùúûü".forEach { put(it, "u") }
    "ýÿ".forEach { put(it, "y") }
    put('œ', "oe")
    put('æ', "ae")
    put('ß', "ss")
    put('’', "'")
}
