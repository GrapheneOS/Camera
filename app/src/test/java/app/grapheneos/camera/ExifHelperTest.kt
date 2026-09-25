package app.grapheneos.camera

import androidxc.exifinterface.media.ExifInterface
import app.grapheneos.camera.testutil.capturedImageBytes
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExifHelperTest {

    @Test
    fun fixExif_writesTheLocalCaptureTime() {
        val exif = fixedExif(zone = "Europe/Amsterdam")

        assertEquals("2026:01:15 09:05:30", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("2026:01:15 09:05:30", exif.getAttribute(ExifInterface.TAG_DATETIME))
    }

    @Test
    fun fixExif_inUtc_writesAZeroOffset() {
        assertEquals("+00:00", offsetTimeOf(fixedExif(zone = "UTC")))
    }

    @Test
    fun fixExif_withAPositiveOffset_padsTheHours() {
        assertEquals("+05:45", offsetTimeOf(fixedExif(zone = "Asia/Kathmandu")))
    }

    @Test
    fun fixExif_withANegativeOffset_padsTheHours() {
        assertEquals("-05:00", offsetTimeOf(fixedExif(zone = "America/New_York")))
    }

    @Test
    fun fixExif_withANegativeHalfHourOffset_signsTheWholeOffset() {
        assertEquals("-03:30", offsetTimeOf(fixedExif(zone = "America/St_Johns")))
    }

    @Test
    fun fixExif_writesTheSameOffsetForTheOriginalTime() {
        val exif = fixedExif(zone = "America/St_Johns")

        assertEquals(
            exif.getAttribute(ExifInterface.TAG_OFFSET_TIME),
            exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
        )
    }

    private fun fixedExif(zone: String): ExifInterface {
        val exif = ExifInterface(ByteArrayInputStream(capturedImageBytes()))
        exif.fixExif(ZonedDateTime.of(CAPTURE_TIME, ZoneId.of(zone)))
        return exif
    }

    private fun offsetTimeOf(exif: ExifInterface): String? {
        return exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)
    }

    private companion object {
        val CAPTURE_TIME: LocalDateTime = LocalDateTime.of(2026, 1, 15, 9, 5, 30)
    }
}
