package app.grapheneos.camera.domain.capture.mapper

import android.location.Location
import androidxc.exifinterface.media.ExifInterface
import app.grapheneos.camera.domain.capture.model.CaptureMetadata
import app.grapheneos.camera.domain.capture.model.CapturedImageExif
import app.grapheneos.camera.testutil.capturedImageBytes
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun map_removingExifFromAnUltraHdrImage_keepsTheXmpThatPointsToItsGainMap() {
        val ultraHdr = mapper.map(
            input(jpegBytes = withXmp(capturedImageBytes()), removeExif = true, isUltraHdr = true),
        )
        val plain = mapper.map(
            input(jpegBytes = withXmp(capturedImageBytes()), removeExif = true),
        )

        assertTrue(exifOf(ultraHdr).hasAttribute(ExifInterface.TAG_XMP))
        assertFalse(exifOf(plain).hasAttribute(ExifInterface.TAG_XMP))
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
        jpegBytes: ByteArray = capturedImageBytes(),
        removeExif: Boolean = false,
        isUltraHdr: Boolean = false,
        orientationDegrees: Int = 0,
        shouldUseExifOrientation: Boolean = false,
        metadata: CaptureMetadata = CaptureMetadata(),
    ): CapturedImageExif {
        return CapturedImageExif(
            jpegBytes = jpegBytes,
            uncroppedJpegBytes = jpegBytes,
            isCropped = false,
            orientationDegrees = orientationDegrees,
            shouldUseExifOrientation = shouldUseExifOrientation,
            metadata = metadata,
            removeExif = removeExif,
            isUltraHdr = isUltraHdr,
            captureTime = ZonedDateTime.of(2026, 7, 24, 15, 30, 12, 0, ZoneOffset.UTC),
        )
    }

    private fun withXmp(jpegBytes: ByteArray): ByteArray {
        val exif = exifOf(jpegBytes)
        exif.setAttribute(ExifInterface.TAG_XMP, XMP)
        val output = ByteArrayOutputStream()
        exif.saveAttributes(ByteArrayInputStream(jpegBytes), output)
        return output.toByteArray()
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
        const val LATITUDE = 52.374
        const val LONGITUDE = 4.9
        const val COORDINATE_TOLERANCE = 0.0001
        const val XMP = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF """ +
            """xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description """ +
            """xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/" hdrgm:Version="1.0"/>""" +
            """</rdf:RDF></x:xmpmeta>"""
    }
}
