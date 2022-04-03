package app.grapheneos.camera

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.grapheneos.camera.ui.activities.MainActivity

class ActivityLifeCycleHelper(
    private val callback: (activity: MainActivity?) -> Unit
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        //nothing to do here
    }

    override fun onActivityStarted(activity: Activity) {
        //nothing to do here
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is MainActivity) {
            callback.invoke(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity is MainActivity) {
            callback.invoke(null)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        //nothing to do here
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        //nothing to do here
    }

    override fun onActivityDestroyed(activity: Activity) {
        //nothing to do here
    }
}
