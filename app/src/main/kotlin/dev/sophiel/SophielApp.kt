package dev.sophiel

import android.app.Activity
import android.app.Application
import android.os.Bundle

class SophielApp : Application() {
    val container by lazy { AppContainer() }

    override fun onCreate() {
        super.onCreate()
        // Ticket 09: our screens hide the masks. Multi-window changes while resumed arrive through
        // MainActivity.onMultiWindowModeChanged; a resize need not relaunch the activity.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) =
                container.ownScreens.update(activity, !activity.isInMultiWindowMode)
            override fun onActivityPaused(activity: Activity) = container.ownScreens.update(activity, false)
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
