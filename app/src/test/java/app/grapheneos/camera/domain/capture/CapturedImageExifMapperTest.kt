package app.grapheneos.camera.domain.capture

import android.location.Location
import androidxc.exifinterface.media.ExifInterface
import app.grapheneos.camera.domain.capture.mapper.CapturedImageExifMapperImpl
import app.grapheneos.camera.domain.capture.model.CaptureMetadata
import app.grapheneos.camera.domain.capture.model.CapturedImageExif
import java.io.ByteArrayInputStream
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CapturedImageExifMapperTest {

    private val mapper = CapturedImageExifMapperImpl()

    @Test
    fun map_withGeoTagging_attachesTheCoordinates() {
        val exif = exifOf(mapper.map(input(metadata = CaptureMetadata(location = location()))))

        val coordinates = requireNotNull(exif.latLong)
        assertEquals(LATITUDE, coordinates[0], COORDINATE_TOLERANCE)
        assertEquals(LONGITUDE, coordinates[1], COORDINATE_TOLERANCE)
    }

    @Test
    fun map_keepingExif_writesTheCaptureTime() {
        val exif = exifOf(mapper.map(input()))

        assertNotNull(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
    }

    @Test
    fun map_removingExif_dropsTheCaptureTime() {
        val exif = exifOf(mapper.map(input(removeExif = true)))

        assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
    }

    @Test
    fun map_removingExif_stillAttachesTheCoordinates() {
        val processed = mapper.map(
            input(
                removeExif = true,
                metadata = CaptureMetadata(location = location()),
            ),
        )

        assertNotNull(exifOf(processed).latLong)
    }

    @Test
    fun map_withoutTheOrientationQuirk_rotatesByTheSensorOrientation() {
        val processed = mapper.map(input(orientationDegrees = 90))

        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, orientationOf(processed))
    }

    @Test
    fun map_withTheOrientationQuirk_keepsTheOrientationOfTheImage() {
        val processed = mapper.map(
            input(
                orientationDegrees = 90,
                shouldUseExifOrientation = true,
            ),
        )

        assertEquals(ExifInterface.ORIENTATION_NORMAL, orientationOf(processed))
    }

    @Test
    fun map_reversedHorizontally_marksTheImageAsMirrored() {
        val processed = mapper.map(input(metadata = CaptureMetadata(reversedHorizontal = true)))

        assertEquals(ExifInterface.ORIENTATION_FLIP_HORIZONTAL, orientationOf(processed))
    }

    private fun input(
        removeExif: Boolean = false,
        orientationDegrees: Int = 0,
        shouldUseExifOrientation: Boolean = false,
        metadata: CaptureMetadata = CaptureMetadata(),
    ): CapturedImageExif {
        val jpegBytes = jpegBytes()

        return CapturedImageExif(
            jpegBytes = jpegBytes,
            uncroppedJpegBytes = jpegBytes,
            isCropped = false,
            orientationDegrees = orientationDegrees,
            shouldUseExifOrientation = shouldUseExifOrientation,
            metadata = metadata,
            removeExif = removeExif,
            captureTime = Date(CAPTURE_TIME_MS),
        )
    }

    private fun jpegBytes(): ByteArray {
        val stream = requireNotNull(javaClass.getResourceAsStream(FIXTURE_NAME)) {
            "missing the $FIXTURE_NAME test fixture"
        }

        return stream.use { it.readBytes() }
    }

    private fun exifOf(jpegBytes: ByteArray): ExifInterface {
        return ExifInterface(ByteArrayInputStream(jpegBytes))
    }

    private fun orientationOf(jpegBytes: ByteArray): Int {
        return exifOf(jpegBytes).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED,
        )
    }

    private fun location(): Location {
        return Location("test").apply {
            latitude = LATITUDE
            longitude = LONGITUDE
        }
    }

    private companion object {
        const val FIXTURE_NAME = "/captured_image.jpg"

        const val CAPTURE_TIME_MS = 1_785_000_000_000L

        const val LATITUDE = 52.374
        const val LONGITUDE = 4.9
        const val COORDINATE_TOLERANCE = 0.0001
    }
}
