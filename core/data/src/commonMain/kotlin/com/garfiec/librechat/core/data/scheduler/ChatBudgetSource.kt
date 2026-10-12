package com.garfiec.librechat.core.data.scheduler

import com.garfiec.librechat.core.model.scheduler.ChatBudget

/**
 * The ceilings of the conversations' key, as the settings read and set them (12/10/2026).
 * [SchedulerRepository] is the one implementation; the seam lets the settings be tested without
 * an HTTP client.
 */
interface ChatBudgetSource {
    /** The ceilings, or null when no scheduler is configured or it predates `/plafonds`. Throws when one failed. */
    suspend fun chatBudget(): ChatBudget?

    /** Sets the three ceilings and answers what the gateway applies; throws `ChatBudgetRefused` on a refusal. */
    suspend fun setChatBudget(day: Double, week: Double, month: Double): ChatBudget
}
