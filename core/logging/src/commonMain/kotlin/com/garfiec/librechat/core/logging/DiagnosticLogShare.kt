package com.garfiec.librechat.core.logging

/**
 * Hands the diagnostic log to the platform's share sheet: Settings → Plateforme → « Partager le
 * journal de diagnostic » (20/10/2026). The buffer is redacted at write time ([DiagnosticLogRepository]),
 * so what leaves is what is on disk. Bound by `:app`; the shell reads it with `getOrNull`.
 */
fun interface DiagnosticLogShare {
    suspend fun share()
}
