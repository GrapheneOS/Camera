package app.grapheneos.camera

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.camera.ui.activities.InAppGallery
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A finger resting on a gallery page has its gesture cancelled when the page goes away. The page
 * used to answer that through whichever page the pager held first, and after the last item was
 * deleted the pager held none.
 */
@RunWith(AndroidJUnit4::class)
class PageDeletedUnderFingerRegressionTest {

    /** Launching an activity into a dozing, locked device gives it no visible window. */
    @get:Rule
    val screenAwake = ScreenAwakeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val resolver = instrumentation.targetContext.contentResolver
    private val stall = StalledMediaScan()
    private var image: Uri? = null

    @After
    fun cleanUp() {
        stall.gate.countDown()
        app.unregisterActivityLifecycleCallbacks(stall)
        image?.let { resolver.delete(it, null, null) }
    }

    @Test
    fun deletingTheLastItem_underARestingFinger_closesTheGallery() {
        // Keeps the device's own media out of the gallery, which then shows only this item
        app.registerActivityLifecycleCallbacks(stall)
        val item = CapturedItem(ITEM_TYPE_IMAGE, "20261012_000000", insertImage())

        ActivityScenario.launch(InAppGallery::class.java).use { scenario ->
            scenario.onActivity { activity ->
                GallerySliderAdapter(activity, arrayListOf(item)).let {
                    activity.gallerySliderAdapter = it
                    activity.gallerySlider.adapter = it
                }
            }
            // An image takes touches only once it is shown and can be zoomed
            waitUntil(scenario, "the image is shown") { activity ->
                activity.findViewById<View>(R.id.slide_preview)?.visibility == View.VISIBLE
            }

            val center = FloatArray(2)
            scenario.onActivity { activity ->
                val page = activity.findViewById<View>(R.id.slide_preview)
                val location = IntArray(2)
                page.getLocationOnScreen(location)
                center[0] = location[0] + page.width / 2f
                center[1] = location[1] + page.height / 2f
            }

            val downTime = SystemClock.uptimeMillis()
            touch(downTime, MotionEvent.ACTION_DOWN, center)
            try {
                scenario.onActivity { activity ->
                    // What the delete dialog does once the item is gone from storage
                    activity.gallerySliderAdapter!!.removeItem(item)
                    layOutNow(activity.gallerySlider)

                    assertTrue(activity.isFinishing)
                }
            } finally {
                // A cancellation, so that lifting the finger cannot tap whatever is underneath
                touch(downTime, MotionEvent.ACTION_CANCEL, center)
            }
        }
    }

    private fun touch(downTime: Long, action: Int, at: FloatArray) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, at[0], at[1], 0)
        try {
            instrumentation.sendPointerSync(event)
        } finally {
            event.recycle()
        }
    }

    /**
     * Lays [view] out now rather than on the next frame, so that a crash in its layout fails the
     * test instead of taking the whole run down with the process.
     */
    private fun layOutNow(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(view.height, View.MeasureSpec.EXACTLY),
        )
        view.layout(view.left, view.top, view.right, view.bottom)
    }

    private fun insertImage(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "page_deleted_under_finger_test.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        image = uri

        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)
        resolver.openOutputStream(uri)!!.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        return uri
    }
}
