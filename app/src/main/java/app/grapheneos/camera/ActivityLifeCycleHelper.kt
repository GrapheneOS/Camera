package app.grapheneos.camera

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.grapheneos.camera.ui.activities.SecureActivity

class ActivityLifeCycleHelper(
    private val onSecureActivityCreated: () -> Unit,
    private val onSecureActivityDestroyed: (isChangingConfigurations: Boolean) -> Unit,
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is SecureActivity) {
            onSecureActivityCreated()
        }
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is SecureActivity) {
            onSecureActivityDestroyed(activity.isChangingConfigurations)
        }
    }
}
