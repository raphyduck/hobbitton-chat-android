package com.garfiec.librechat.core.common.diagnostics

import com.garfiec.librechat.core.common.di.AppThreads

private const val POOLS_SHOWN = 6
private const val FRAMES_SHOWN = 4
private const val PLACES_SHOWN = 3
private const val REPORT_MAX_CHARS = 3_000

/** A thread's pool: its name without the trailing number (`DefaultDispatcher-worker-12` → `DefaultDispatcher-worker`). */
private fun poolOf(name: String): String = name.replace(Regex("[-_ #]?\\d+$"), "").ifBlank { name }

/** Where a thread stands, skipping the frames every waiting thread shares. */
private fun placeOf(frames: Array<StackTraceElement>): String =
    frames.asSequence()
        .filterNot { frame ->
            val c = frame.className
            c.startsWith("java.lang.Object") || c.startsWith("sun.misc.Unsafe") ||
                c.startsWith("jdk.internal.misc.Unsafe") || c.startsWith("java.util.concurrent.locks.LockSupport") ||
                c.startsWith("dalvik.system.VMStack") || c.startsWith("java.lang.Thread")
        }
        .take(FRAMES_SHOWN)
        .joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}" }
        .ifBlank { "?" }

actual fun threadReport(): String = runCatching {
    val all = Thread.getAllStackTraces()
    val byPool = all.entries.groupBy { poolOf(it.key.name) }
    buildString {
        append("threads=").append(all.size)
        append(" eva-io=").append(AppThreads.ioThreads)
        append(" eva-net=").append(AppThreads.netThreads)
        byPool.entries.sortedByDescending { it.value.size }.take(POOLS_SHOWN).forEach { (pool, threads) ->
            val states = threads.groupingBy { it.key.state.name.lowercase() }.eachCount()
                .entries.joinToString(",") { "${it.key}:${it.value}" }
            append(" | ").append(pool).append(" ×").append(threads.size).append(" (").append(states).append(")")
            threads.groupingBy { placeOf(it.value) }.eachCount()
                .entries.sortedByDescending { it.value }.take(PLACES_SHOWN)
                .forEach { (place, count) -> append(" [").append(count).append("] ").append(place) }
        }
    }.take(REPORT_MAX_CHARS)
}.getOrElse { "thread report failed: ${it::class.simpleName}" }
