package com.garfiec.librechat

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.Job

/**
 * Runs [start] while an activity of the app is started, and cancels what it returned once none is
 * (26/10/2026). One activity in this app, so a counter is enough and no dependency on the process
 * lifecycle library is needed.
 */
internal class ForegroundWatchdog(private val start: () -> Job) : Application.ActivityLifecycleCallbacks {

    private var started = 0
    private var job: Job? = null

    override fun onActivityStarted(activity: Activity) {
        if (started++ == 0) job = start()
    }

    override fun onActivityStopped(activity: Activity) {
        if (--started <= 0) {
            started = 0
            job?.cancel()
            job = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
