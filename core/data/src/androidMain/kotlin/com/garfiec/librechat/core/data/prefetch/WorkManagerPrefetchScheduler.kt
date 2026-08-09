package com.garfiec.librechat.core.data.prefetch

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules [PrefetchWorker] to warm the cache while the device is idle and on power.
 *
 * The constraints are the feature, not decoration. Charging plus device-idle means this realistically
 * runs overnight rather than on any fixed cadence — the interval below is how often the system is
 * *allowed* to consider it, not how often it fires. Daytime warming comes from a pass outliving the
 * foreground instead, which is why the two mechanisms ship together.
 */
class WorkManagerPrefetchScheduler(private val context: Context) : PrefetchScheduler {

    override val isSupported: Boolean = true

    override fun ensureScheduled(allowMetered: Boolean) {
        // What the job was last registered with. Persisted because the comparison has to survive
        // process death — this runs at every cold start, and the setting it depends on can change
        // while the app is not running. Absent reads as "unchanged": that means nothing is pending,
        // and KEEP inserts in that case anyway, so both policies agree there.
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val constraintsChanged = prefs.getBoolean(KEY_SCHEDULED_METERED, allowMetered) != allowMetered

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<PrefetchWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        // KEEP unless the constraints actually changed. This runs at every process start, including
        // the process a scheduled run itself woke, and re-registering identical work there churns
        // the WorkSpec for no benefit — UPDATE bumps its generation and reschedules on a path that
        // had nothing to change.
        //
        // UPDATE is still right when the network constraint genuinely changed, because a job carries
        // its constraints and KEEP would leave the old one in place with nothing to show the setting
        // had no effect. That only happens from a foreground toggle.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            if (constraintsChanged) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        prefs.edit().putBoolean(KEY_SCHEDULED_METERED, allowMetered).apply()
    }

    override fun cancel() {
        // The stored constraint is deliberately left alone: cancelled work is a finished state, so
        // it is no longer pending and the next KEEP enqueue inserts regardless of what it says.
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "librechat-prefetch"
        const val INTERVAL_HOURS = 6L
        const val PREFS_NAME = "prefetch_schedule"
        const val KEY_SCHEDULED_METERED = "scheduled_allow_metered"
    }
}
