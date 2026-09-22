package app.grapheneos.camera.data.camera.session

import android.graphics.BitmapFactory
import app.grapheneos.camera.data.camera.model.CapturedJpeg
import app.grapheneos.camera.data.camera.model.CapturedJpegCropRect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JpegExtractorImplTest {

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
                    right = FIXTURE_SIZE,
                    bottom = FIXTURE_SIZE,
                ),
            ),
            jpegQuality = JPEG_QUALITY,
        )

        val bounds = boundsOf(cropped)
        assertEquals(FIXTURE_SIZE, bounds.outWidth)
        assertEquals(FIXTURE_SIZE, bounds.outHeight)
    }

    private fun capturedJpeg(cropRect: CapturedJpegCropRect): CapturedJpeg {
        return CapturedJpeg(
            jpegBytes = fixtureBytes(),
            cropRect = cropRect,
            orientationDegrees = 0,
            shouldUseExifOrientation = false,
        )
    }

    private fun fixtureBytes(): ByteArray {
        val stream = requireNotNull(javaClass.getResourceAsStream(FIXTURE_NAME)) {
            "missing the $FIXTURE_NAME test fixture"
        }

        return stream.use { it.readBytes() }
    }

    private fun boundsOf(jpegBytes: ByteArray): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, options)
        return options
    }

    private companion object {
        const val FIXTURE_NAME = "/captured_image.jpg"
        const val FIXTURE_SIZE = 16
        const val JPEG_QUALITY = 95
    }
}
