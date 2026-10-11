package com.sanwolf.daw

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.StrictMode

/** Installs crash capture before anything else runs (registered in AndroidManifest.xml). */
class SanwolfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        if (BuildConfig.DEBUG) {
            // Debug builds only: log (never crash on) main-thread disk / network access
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build()
            )
        }
        // Foreground / background transitions in the breadcrumbs ("went home" before a crash?)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                CrashReporter.breadcrumb("${activity.javaClass.simpleName} created${if (savedInstanceState != null) " (restored)" else ""}")
            }
            override fun onActivityStarted(activity: Activity) {
                CrashReporter.breadcrumb("${activity.javaClass.simpleName} foreground")
            }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                CrashReporter.breadcrumb("${activity.javaClass.simpleName} background")
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                CrashReporter.breadcrumb("${activity.javaClass.simpleName} destroyed")
            }
        })
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        CrashReporter.breadcrumb("onTrimMemory($level)")
    }
}
