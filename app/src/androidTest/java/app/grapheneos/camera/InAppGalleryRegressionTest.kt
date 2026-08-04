package app.grapheneos.camera

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.widget.PopupMenu
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.camera.ui.activities.InAppGallery
import java.util.concurrent.CountDownLatch
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InAppGalleryRegressionTest {

    /** Launching an activity into a dozing, locked device gives it no visible window. */
    @get:Rule
    val screenAwake = ScreenAwakeRule()

    private val app = InstrumentationRegistry.getInstrumentation()
        .targetContext.applicationContext as Application
    private val stall = StalledMediaScan()

    @After
    fun releaseScan() {
        stall.gate.countDown()
        app.unregisterActivityLifecycleCallbacks(stall)
    }

    /**
     * Regression test for the production crash: tapping a media action while the media scan
     * was still running dereferenced the null adapter (NPE in getCurrentItem).
     */
    @Test
    fun mediaActions_beforeMediaScanCompletes_doNotCrash() {
        app.registerActivityLifecycleCallbacks(stall)
        ActivityScenario.launch(InAppGallery::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNull(activity.gallerySliderAdapter)

                val menu = PopupMenu(activity, activity.window.decorView).menu
                activity.onCreateOptionsMenu(menu)
                for (id in intArrayOf(
                    R.id.delete_icon,
                    R.id.share_icon,
                    R.id.info,
                    R.id.edit_icon
                )) {
                    activity.onOptionsItemSelected(menu.findItem(id))
                }

                assertFalse(activity.isFinishing)
            }
        }
    }

    /**
     *  The media actions must not be offered before there is media to act on.
     */
    @Test
    fun mediaActionsMenu_hiddenUntilMediaScanCompletes() {
        app.registerActivityLifecycleCallbacks(stall)
        ActivityScenario.launch(InAppGallery::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val menu = PopupMenu(activity, activity.window.decorView).menu
                activity.onCreateOptionsMenu(menu)

                activity.onPrepareOptionsMenu(menu)
                assertFalse(menu.findItem(R.id.delete_icon).isVisible)
                assertFalse(menu.findItem(R.id.share_icon).isVisible)

                val item = CapturedItem(
                    ITEM_TYPE_IMAGE,
                    "20260724_000000",
                    Uri.parse("content://media/external/images/media/0"),
                )
                activity.gallerySliderAdapter = GallerySliderAdapter(activity, arrayListOf(item))
                activity.onPrepareOptionsMenu(menu)
                assertTrue(menu.findItem(R.id.delete_icon).isVisible)
            }
        }
    }

    /**
     * Holds the gallery's media scan so gallerySliderAdapter stays null, like on a slow device.
     */
    private class StalledMediaScan : Application.ActivityLifecycleCallbacks {
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
}
