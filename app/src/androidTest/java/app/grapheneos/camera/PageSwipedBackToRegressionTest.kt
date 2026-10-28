package app.grapheneos.camera

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import app.grapheneos.camera.ui.ZoomableImageView
import app.grapheneos.camera.ui.activities.InAppGallery
import app.grapheneos.camera.ui.fragment.GallerySlide
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Swiping back to a gallery page leaves the page swiped away from laid out over it, faded out
 * entirely, and that page took the touches meant for the one shown.
 */
@RunWith(AndroidJUnit4::class)
class PageSwipedBackToRegressionTest {

    /** Launching an activity into a dozing, locked device gives it no visible window. */
    @get:Rule
    val screenAwake = ScreenAwakeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val resolver = instrumentation.targetContext.contentResolver
    private val stall = StalledMediaScan()
    private val images = ArrayList<Uri>()

    @After
    fun cleanUp() {
        stall.gate.countDown()
        app.unregisterActivityLifecycleCallbacks(stall)
        images.forEach { resolver.delete(it, null, null) }
    }

    @Test
    fun doubleTap_onAPageSwipedBackTo_zoomsThatPage() {
        // Keeps the device's own media out of the gallery, which then shows only these items
        app.registerActivityLifecycleCallbacks(stall)
        val items = arrayListOf(
            CapturedItem(ITEM_TYPE_IMAGE, "20261015_000001", insertImage("newer")),
            CapturedItem(ITEM_TYPE_IMAGE, "20261015_000000", insertImage("older")),
        )

        ActivityScenario.launch(InAppGallery::class.java).use { scenario ->
            scenario.onActivity { activity ->
                GallerySliderAdapter(activity, items).let {
                    activity.gallerySliderAdapter = it
                    activity.gallerySlider.adapter = it
                }
            }
            // An image takes touches only once it is shown and can be zoomed, so the page swiped
            // away from has to be as well, or it lets them through whether or not it is in the way
            awaitShown(scenario, 0)
            swipe(scenario, towards = 1)
            swipe(scenario, towards = 0)

            var zoomBefore = 0f
            val center = FloatArray(2)
            scenario.onActivity { activity ->
                assertNotNull(
                    "the page swiped away from is no longer laid out, so nothing is in the way",
                    page(activity, 1),
                )
                val preview = page(activity, 0)!!
                zoomBefore = zoomOf(preview)
                val location = IntArray(2)
                preview.getLocationOnScreen(location)
                center[0] = location[0] + preview.width / 2f
                center[1] = location[1] + preview.height / 2f
            }

            tap(center)
            tap(center)

            waitUntil(scenario, "the page shown zooms in") { activity ->
                zoomOf(page(activity, 0)!!) > zoomBefore * 1.5f
            }
        }
    }

    private fun awaitShown(scenario: ActivityScenario<InAppGallery>, position: Int) {
        waitUntil(scenario, "page $position is shown") { activity ->
            activity.gallerySlider.currentItem == position &&
                    activity.gallerySlider.scrollState == ViewPager2.SCROLL_STATE_IDLE &&
                    page(activity, position)?.visibility == View.VISIBLE
        }
    }

    /** Swipes the pager by a page with a real gesture, the way that leaves a page behind. */
    private fun swipe(scenario: ActivityScenario<InAppGallery>, towards: Int) {
        val bounds = IntArray(4)
        scenario.onActivity { activity ->
            val location = IntArray(2)
            activity.gallerySlider.getLocationOnScreen(location)
            bounds[0] = location[0]
            bounds[1] = location[1]
            bounds[2] = activity.gallerySlider.width
            bounds[3] = activity.gallerySlider.height
        }
        val y = bounds[1] + bounds[3] / 2f
        val (fromX, toX) = (bounds[0] + bounds[2] * 0.8f) to (bounds[0] + bounds[2] * 0.2f)
        val (startX, endX) = if (towards == 1) fromX to toX else toX to fromX

        val downTime = SystemClock.uptimeMillis()
        send(downTime, MotionEvent.ACTION_DOWN, startX, y)
        for (step in 1..SWIPE_STEPS) {
            Thread.sleep(SWIPE_STEP_MS)
            send(downTime, MotionEvent.ACTION_MOVE, startX + (endX - startX) * step / SWIPE_STEPS, y)
        }
        send(downTime, MotionEvent.ACTION_UP, endX, y)

        awaitShown(scenario, towards)
    }

    private fun tap(at: FloatArray) {
        val downTime = SystemClock.uptimeMillis()
        send(downTime, MotionEvent.ACTION_DOWN, at[0], at[1])
        send(downTime, MotionEvent.ACTION_UP, at[0], at[1])
    }

    private fun send(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        try {
            instrumentation.sendPointerSync(event)
        } finally {
            event.recycle()
        }
    }

    private fun page(activity: InAppGallery, position: Int): ZoomableImageView? {
        val pages = activity.gallerySlider.getChildAt(0) as RecyclerView
        val holder = pages.findViewHolderForAdapterPosition(position) as GallerySlide?
        return holder?.binding?.slidePreview
    }

    private fun zoomOf(preview: ZoomableImageView): Float {
        val values = FloatArray(9)
        preview.imageMatrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    private fun insertImage(name: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "page_swiped_back_to_test_$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        images += uri

        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)
        resolver.openOutputStream(uri)!!.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        return uri
    }

    private companion object {
        const val SWIPE_STEPS = 10
        const val SWIPE_STEP_MS = 10L
    }
}
