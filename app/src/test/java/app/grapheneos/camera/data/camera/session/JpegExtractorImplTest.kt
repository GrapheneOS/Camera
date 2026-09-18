package app.grapheneos.camera.data.camera.session

import android.graphics.BitmapFactory
import android.graphics.Rect
import app.grapheneos.camera.data.camera.model.CapturedJpeg
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
            jpeg = capturedJpeg(cropRect = Rect(2, 4, 12, 10)),
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
            jpeg = capturedJpeg(cropRect = Rect(0, 0, FIXTURE_SIZE, FIXTURE_SIZE)),
            jpegQuality = JPEG_QUALITY,
        )

        val bounds = boundsOf(cropped)
        assertEquals(FIXTURE_SIZE, bounds.outWidth)
        assertEquals(FIXTURE_SIZE, bounds.outHeight)
    }

    private fun capturedJpeg(cropRect: Rect): CapturedJpeg {
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
