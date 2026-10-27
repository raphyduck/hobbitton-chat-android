package com.garfiec.librechat.core.common.diagnostics

/**
 * The process's threads in a few lines, for the journal (27/10/2026): how many of each pool, in
 * which state, and where the busiest ones stand. Written when something waited for a thread and
 * none came (an opening still on its placeholders, the shared IO threads late to answer), so the
 * next journal names what held them instead of a guess.
 */
expect fun threadReport(): String
