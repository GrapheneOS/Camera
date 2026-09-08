package app.grapheneos.camera

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.media.Image
import android.net.Uri
import android.view.View
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.grapheneos.camera.capturer.ImageSaver
import app.grapheneos.camera.ui.activities.MainActivity
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
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
            waitUntil(scenario, "camera is bound") { it.session.camera != null }

            // Stands in for the thumbnail of the capture before this one
            val previousThumbnail = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            var capturedBefore: Uri? = null
            lateinit var saver: ImageSaver
            scenario.onActivity { activity ->
                capturedBefore = activity.camConfig.lastCapturedItem?.uri
                activity.imagePreview.setImageBitmap(previousThumbnail)

                // What ImageCapturer.takePicture() sets up, saving to MediaStore so that the
                // capture can be read back and deleted
                saver = ImageSaver(
                    activity.imageCapturer,
                    activity.applicationContext,
                    jpegQuality = 95,
                    CamConfig.SettingValues.Default.STORAGE_LOCATION,
                    ".jpg",
                    ImageCapture.Metadata(),
                    removeExifAfterCapture = false,
                    targetThumbnailWidth = activity.imagePreview.width,
                    targetThumbnailHeight = activity.imagePreview.height,
                )
            }

            // An uncaught exception on any thread kills the process, which would end the whole run
            // rather than fail this test
            val crash = AtomicReference<Throwable>()
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { _, e -> crash.set(e) }
            try {
                ImageSaver.imageCaptureCallbackExecutor.execute {
                    saver.onCaptureSuccess(JpegImageProxy(jpeg, WIDTH, HEIGHT))
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

    /** Noise keeps the scan data long, so that cutting it in half lands well inside it. */
    private fun noiseJpeg(): ByteArray {
        val random = Random(0)
        val pixels = IntArray(WIDTH * HEIGHT) { random.nextInt() or (0xff shl 24) }
        val bitmap = Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            it.toByteArray()
        }
    }

    /** The offset of the SOS marker, after which nothing but scan data follows. */
    private fun startOfScan(jpeg: ByteArray): Int {
        for (i in 0 until jpeg.size - 1) {
            if (jpeg[i] == 0xff.toByte() && jpeg[i + 1] == 0xda.toByte()) return i
        }
        throw AssertionError("no SOS marker")
    }

    /** Keeps test runs from accumulating images on the device. */
    private fun deleteNewCapture(scenario: ActivityScenario<MainActivity>, previous: Uri?) {
        scenario.onActivity { activity ->
            val item = activity.camConfig.lastCapturedItem ?: return@onActivity
            if (item.uri != previous) {
                activity.contentResolver.delete(item.uri, null, null)
            }
        }
    }

    /** What ImageCapture hands the saver for a JPEG capture: the bytes the HAL produced. */
    @SuppressLint("RestrictedApi")
    private class JpegImageProxy(
        private val jpeg: ByteArray,
        private val width: Int,
        private val height: Int,
    ) : ImageProxy {
        private var cropRect = Rect(0, 0, width, height)

        override fun close() {}
        override fun getCropRect() = Rect(cropRect)
        override fun setCropRect(rect: Rect?) {
            cropRect = rect ?: Rect(0, 0, width, height)
        }
        override fun getFormat() = ImageFormat.JPEG
        override fun getWidth() = width
        override fun getHeight() = height
        override fun getPlanes(): Array<ImageProxy.PlaneProxy> = arrayOf(
            object : ImageProxy.PlaneProxy {
                override fun getRowStride() = 0
                override fun getPixelStride() = 0
                override fun getBuffer(): ByteBuffer = ByteBuffer.wrap(jpeg)
            }
        )
        override fun getImageInfo(): ImageInfo = object : ImageInfo {
            override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()
            override fun getTimestamp() = 0L
            override fun getRotationDegrees() = 0
            override fun populateExifData(exifBuilder: ExifData.Builder) {}
        }
        override fun getImage(): Image? = null
    }

    private companion object {
        const val WIDTH = 640
        const val HEIGHT = 480
    }
}
