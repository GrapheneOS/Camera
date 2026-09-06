package app.grapheneos.camera

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.grapheneos.camera.ui.activities.MainActivity
import app.grapheneos.camera.ui.activities.SecureActivity

class ActivityLifeCycleHelper(
    private val onResumedActivityChanged: (activity: MainActivity?) -> Unit,
    private val onSecureActivityCountChanged: (opened: Boolean) -> Unit,
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is SecureActivity) {
            onSecureActivityCountChanged(true)
        }
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {
        if (activity is MainActivity) {
            onResumedActivityChanged(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity is MainActivity) {
            onResumedActivityChanged(null)
        }
    }

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is SecureActivity) {
            onSecureActivityCountChanged(false)
        }
    }
}
