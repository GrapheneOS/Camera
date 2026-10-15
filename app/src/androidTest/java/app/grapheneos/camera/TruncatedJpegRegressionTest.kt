package app.grapheneos.camera

import android.Manifest
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.capturer.ImageSaver
import app.grapheneos.camera.ui.activities.MainActivity
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Some camera HALs hand over a JPEG that ends early. It is saved like any other capture, but the
 * thumbnail decode then threw on the thumbnail thread and took the process down with it.
 */
@RunWith(AndroidJUnit4::class)
class TruncatedJpegRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
    )

    /** The capture UI that the saver reports to needs a resumed, visible activity. */
    @get:Rule
    val screenAwake = ScreenAwakeRule()

    /** Decoding runs out of input part way through the image ("Input was incomplete."). */
    @Test
    fun jpegCutShortInItsScanData_isKeptWithoutAThumbnail() {
        truncatedJpegTest { jpeg -> jpeg.size / 2 }
    }

    /**
     * The decoder cannot even be created. The Exif rewrite copies everything from the SOS marker on
     * without looking at it, so a JPEG that ends right after the marker is still saved.
     */
    @Test
    fun jpegCutShortInItsScanHeader_isKeptWithoutAThumbnail() {
        truncatedJpegTest { jpeg -> startOfScan(jpeg) + 2 }
    }

    private fun truncatedJpegTest(cutAt: (ByteArray) -> Int) {
        val whole = noiseJpeg()
        val jpeg = whole.copyOf(cutAt(whole))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "camera is bound") { it.camConfig.camera != null }

            // Stands in for the thumbnail of the capture before this one
            val previousThumbnail = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            var capturedBefore: Uri? = null
            lateinit var saver: ImageSaver
            scenario.onActivity { activity ->
                capturedBefore = activity.camConfig.lastCapturedItem?.uri
                activity.imagePreview.setImageBitmap(previousThumbnail)
                saver = imageSaverFor(activity)
            }

            // An uncaught exception on any thread kills the process, which would end the whole run
            // rather than fail this test
            val crash = AtomicReference<Throwable>()
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { _, e -> crash.set(e) }
            try {
                ImageSaver.imageCaptureCallbackExecutor.execute {
                    saver.onCaptureSuccess(JpegImageProxy(jpeg))
                }

                // The loader is shown before the capture is saved, so once the capture is saved a
                // hidden loader means the thumbnail stage is over
                waitUntil(scenario, "the thumbnail stage is over") { activity ->
                    val isSaved = activity.camConfig.lastCapturedItem?.uri != capturedBefore
                    crash.get() != null
                            || (isSaved && activity.previewLoader.visibility == View.GONE)
                }
                crash.get()?.let {
                    throw AssertionError("the thumbnail stage crashed the process", it)
                }

                var saved: Uri? = null
                var shownThumbnail: Bitmap? = null
                scenario.onActivity { activity ->
                    saved = activity.camConfig.lastCapturedItem?.uri
                    shownThumbnail = (activity.imagePreview.drawable as? BitmapDrawable)?.bitmap
                }
                assertNull("a thumbnail is shown for a capture that has none", shownThumbnail)

                val bytes = targetContext.contentResolver.openInputStream(saved!!)?.use {
                    it.readBytes()
                }
                assertNotNull("the capture was deleted", bytes)
                // Everything from the SOS marker on is written out exactly as the HAL produced it
                val scan = jpeg.copyOfRange(startOfScan(jpeg), jpeg.size)
                assertArrayEquals(scan, bytes!!.copyOfRange(bytes.size - scan.size, bytes.size))
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(defaultHandler)
                runCatching { deleteNewCapture(scenario, capturedBefore) }
            }
        }
    }

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Keeps test runs from accumulating images on the device. */
    private fun deleteNewCapture(scenario: ActivityScenario<MainActivity>, previous: Uri?) {
        scenario.onActivity { activity ->
            val item = activity.camConfig.lastCapturedItem ?: return@onActivity
            if (item.uri != previous) {
                activity.contentResolver.delete(item.uri, null, null)
            }
        }
    }
}
