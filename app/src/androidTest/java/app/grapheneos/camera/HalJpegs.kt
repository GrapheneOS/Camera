package app.grapheneos.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import app.grapheneos.camera.capturer.ImageSaver
import app.grapheneos.camera.ui.activities.MainActivity
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.random.Random

const val HAL_JPEG_WIDTH = 640
const val HAL_JPEG_HEIGHT = 480

/**
 * What ImageCapturer.takePicture() sets up, saving to MediaStore so that the capture can be read
 * back and deleted.
 */
fun imageSaverFor(activity: MainActivity) = ImageSaver(
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

/** Noise keeps the scan data long, so that cutting it in half lands well inside it. */
fun noiseJpeg(): ByteArray {
    val random = Random(0)
    val pixels = IntArray(HAL_JPEG_WIDTH * HAL_JPEG_HEIGHT) { random.nextInt() or (0xff shl 24) }
    val bitmap =
        Bitmap.createBitmap(pixels, HAL_JPEG_WIDTH, HAL_JPEG_HEIGHT, Bitmap.Config.ARGB_8888)
    return ByteArrayOutputStream().use {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        it.toByteArray()
    }
}

/** The offset of the SOS marker, after which nothing but scan data follows. */
fun startOfScan(jpeg: ByteArray): Int {
    for (i in 0 until jpeg.size - 1) {
        if (jpeg[i] == 0xff.toByte() && jpeg[i + 1] == 0xda.toByte()) return i
    }
    throw AssertionError("no SOS marker")
}

/** What ImageCapture hands the saver for a JPEG capture: the buffer the HAL filled. */
@SuppressLint("RestrictedApi")
class JpegImageProxy(private val bytes: ByteArray) : ImageProxy {
    private var cropRect = Rect(0, 0, HAL_JPEG_WIDTH, HAL_JPEG_HEIGHT)

    override fun close() {}
    override fun getCropRect() = Rect(cropRect)
    override fun setCropRect(rect: Rect?) {
        cropRect = rect ?: Rect(0, 0, HAL_JPEG_WIDTH, HAL_JPEG_HEIGHT)
    }
    override fun getFormat() = ImageFormat.JPEG
    override fun getWidth() = HAL_JPEG_WIDTH
    override fun getHeight() = HAL_JPEG_HEIGHT
    override fun getPlanes(): Array<ImageProxy.PlaneProxy> = arrayOf(
        object : ImageProxy.PlaneProxy {
            override fun getRowStride() = 0
            override fun getPixelStride() = 0
            override fun getBuffer(): ByteBuffer = ByteBuffer.wrap(bytes)
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
