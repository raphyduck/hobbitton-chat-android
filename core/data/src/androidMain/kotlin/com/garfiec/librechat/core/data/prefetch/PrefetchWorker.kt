package com.garfiec.librechat.core.data.prefetch

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.garfiec.librechat.core.logging.Diag
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.minutes

/**
 * Runs one prefetch pass on the schedule set by [WorkManagerPrefetchScheduler].
 *
 * Resolves from Koin rather than taking constructor injection: the graph is started in
 * `Application.onCreate`, and a worker is only ever instantiated when its job runs, which is always
 * afterwards. That avoids a custom `WorkerFactory` and a `Configuration.Provider` on the Application
 * for a single worker.
 */
class PrefetchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val runner: PrefetchBackgroundRunner by inject()

    override suspend fun doWork(): Result {
        val outcome = runner.runOnce(BUDGET)
        Diag.d("Prefetch", attrs = mapOf("outcome" to outcome.name)) { "worker finished" }

        // Always success, never retry. `setRequiresDeviceIdle` suppresses WorkManager's backoff, so
        // a retry would not be rescheduled on a ladder — it would simply wait for the next period,
        // which is what returning success does anyway, without the failure showing up in job
        // diagnostics as though something were wrong. Nothing here is lost by waiting: watermarks
        // make a partial pass resumable, and an unmet constraint is not an error.
        return Result.success()
    }

    private companion object {
        /**
         * Comfortably inside WorkManager's ~10-minute execution window, leaving room for the pass to
         * unwind. A pass that outruns it is not lost — it resumes from its watermarks next time.
         */
        val BUDGET = 8.minutes
    }
}
