package app.grapheneos.camera.data.camera.session

import android.graphics.BitmapFactory
import app.grapheneos.camera.data.camera.model.CapturedJpeg
import app.grapheneos.camera.data.camera.model.CapturedJpegCropRect
import app.grapheneos.camera.testutil.CAPTURED_IMAGE_SIZE
import app.grapheneos.camera.testutil.capturedImageBytes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JpegExtractorTest {

    private val extractor = JpegExtractorImpl()

    @Test
    fun crop_encodesAJpegOfTheCropRectSize() {
        val cropped = extractor.crop(
            jpeg = capturedJpeg(
                cropRect = CapturedJpegCropRect(
                    left = 2,
                    top = 4,
                    right = 12,
                    bottom = 10,
                ),
            ),
            jpegQuality = JPEG_QUALITY,
        )

        val bounds = boundsOf(cropped)
        assertEquals("image/jpeg", bounds.outMimeType)
        assertEquals(10, bounds.outWidth)
        assertEquals(6, bounds.outHeight)
    }

    @Test
    fun crop_toTheWholeImage_keepsItsSize() {
        val cropped = extractor.crop(
            jpeg = capturedJpeg(
                cropRect = CapturedJpegCropRect(
                    left = 0,
                    top = 0,
                    right = CAPTURED_IMAGE_SIZE,
                    bottom = CAPTURED_IMAGE_SIZE,
                ),
            ),
            jpegQuality = JPEG_QUALITY,
        )

        val bounds = boundsOf(cropped)
        assertEquals(CAPTURED_IMAGE_SIZE, bounds.outWidth)
        assertEquals(CAPTURED_IMAGE_SIZE, bounds.outHeight)
    }

    private fun capturedJpeg(cropRect: CapturedJpegCropRect): CapturedJpeg {
        return CapturedJpeg(
            jpegBytes = capturedImageBytes(),
            cropRect = cropRect,
            orientationDegrees = 0,
            shouldUseExifOrientation = false,
        )
    }

    private fun boundsOf(jpegBytes: ByteArray): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options)
        return options
    }

    private companion object {
        const val JPEG_QUALITY = 95
    }
}
