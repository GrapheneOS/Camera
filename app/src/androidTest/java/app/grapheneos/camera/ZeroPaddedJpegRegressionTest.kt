package app.grapheneos.camera

import android.Manifest
import android.net.Uri
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.capturer.ImageSaver
import app.grapheneos.camera.ui.activities.MainActivity
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Some capture pipelines, such as vendor extension modes, hand a JPEG over in a buffer far larger
 * than the JPEG and zeroed past its end, and the whole buffer was saved as the photo: megabytes of
 * zeros after the image, in every photo taken in those modes.
 */
@RunWith(AndroidJUnit4::class)
class ZeroPaddedJpegRegressionTest {

    @get:Rule
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
    )

    /** The capture UI that the saver reports to needs a resumed, visible activity. */
    @get:Rule
    val screenAwake = ScreenAwakeRule()

    @Test
    fun jpegInAZeroPaddedBuffer_isSavedWithoutThePadding() {
        val jpeg = noiseJpeg()
        val buffer = jpeg.copyOf(jpeg.size * 4)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitUntil(scenario, "camera is bound") { it.camConfig.camera != null }

            var capturedBefore: Uri? = null
            lateinit var saver: ImageSaver
            scenario.onActivity { activity ->
                capturedBefore = activity.camConfig.lastCapturedItem?.uri
                saver = imageSaverFor(activity)
            }

            try {
                ImageSaver.imageCaptureCallbackExecutor.execute {
                    saver.onCaptureSuccess(JpegImageProxy(buffer))
                }

                // The loader is shown before the capture is saved, so once the capture is saved a
                // hidden loader means the thumbnail stage is over
                var saved: Uri? = null
                waitUntil(scenario, "the thumbnail stage is over") { activity ->
                    saved = activity.camConfig.lastCapturedItem?.uri
                    saved != capturedBefore && activity.previewLoader.visibility == View.GONE
                }

                val bytes = targetContext.contentResolver.openInputStream(saved!!)!!.use {
                    it.readBytes()
                }
                // The saver rewrites the Exif, which comes before the SOS marker
                val scan = jpeg.copyOfRange(startOfScan(jpeg), jpeg.size)
                assertArrayEquals(
                    "the photo is not saved up to exactly where the JPEG ends",
                    scan, bytes.copyOfRange(bytes.size - scan.size, bytes.size),
                )
            } finally {
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
