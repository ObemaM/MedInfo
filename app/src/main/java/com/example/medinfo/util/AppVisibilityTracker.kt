package com.example.medinfo.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

object AppVisibilityTracker : Application.ActivityLifecycleCallbacks {

    private val startedActivitiesCount = AtomicInteger(0)

    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null

    val isAppInForeground: Boolean
        get() = startedActivitiesCount.get() > 0

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    fun currentActivity(): Activity? {
        val activity = currentActivityRef?.get() ?: return null
        return activity.takeUnless { it.isFinishing || it.isDestroyed }
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivitiesCount.incrementAndGet()
    }

    override fun onActivityStopped(activity: Activity) {
        if (startedActivitiesCount.decrementAndGet() < 0) {
            startedActivitiesCount.set(0)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivityRef?.get() == activity) {
            currentActivityRef = null
        }
    }
}
