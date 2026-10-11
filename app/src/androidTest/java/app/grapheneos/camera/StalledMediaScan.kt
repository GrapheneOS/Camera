package app.grapheneos.camera

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.grapheneos.camera.ui.activities.InAppGallery
import java.util.concurrent.CountDownLatch

/**
 * Holds the gallery's media scan so gallerySliderAdapter stays null, like on a slow device.
 */
class StalledMediaScan : Application.ActivityLifecycleCallbacks {
    val gate = CountDownLatch(1)

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is InAppGallery) {
            activity.asyncLoaderOfCapturedItems.execute {
                // Interrupted by shutdownNow() when the activity is destroyed
                try {
                    gate.await()
                } catch (_: InterruptedException) {
                }
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
